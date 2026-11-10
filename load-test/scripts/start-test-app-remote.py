#!/usr/bin/env python3
"""테스트 EC2에서 JFR 저장소 경로를 추가한 테스트 앱을 새 컨테이너로 한 번 기동한다.

정지 상태의 템플릿 컨테이너 설정(이미지·환경·인자·포트)을 복제하고, 다른 컨테이너는 중지·시작·삭제하지 않는다.
(지난 operation-start-jfr-app-remote.py는 실패 시 원본을 다시 켰다 — 여기서는 하지 않는다.)
사용법: ssh … python3 - '<JSON 설정>' < start-test-app-remote.py   (JSON 키는 아래 C 참조, 비밀값 없음)
"""
import json
import os
import pathlib
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request

C = json.loads(sys.argv[1])


def run(args, timeout=30):
    p = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError('command_failed:%s:%s' % (' '.join(args[:3]), p.stderr.strip()[:200]))
    return p.stdout


def need(condition, message):
    if not condition:
        raise RuntimeError('precondition_failed:' + message)


def listening(port):
    s = socket.socket()
    s.settimeout(1)
    try:
        return s.connect_ex(('127.0.0.1', port)) == 0
    finally:
        s.close()


def main():
    tpl = json.loads(run(['docker', 'inspect', C['template']]))[0]
    env = dict(x.split('=', 1) for x in tpl['Config']['Env'] if '=' in x)
    need(tpl['Image'] == C['image_id'], 'image_id')
    need(tpl['Config']['Entrypoint'] == ['java', '-jar', 'app.jar'], 'entrypoint')
    need(tpl['HostConfig']['Memory'] == 0 and tpl['HostConfig']['NanoCpus'] == 0, 'resource_limits')
    need(tpl['HostConfig']['NetworkMode'] in ('default', 'bridge'), 'network_mode')
    need(env.get('DB_USERNAME') == C['db_user'] and env.get('DB_URL', '').endswith('/' + C['db_name']), 'db_identity')
    need(all('-Xrs' not in env.get(k, '') for k in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')), 'xrs')
    need('--triagain.crew.lock-strategy=CONDITIONAL' in tpl['Args'], 'lock_strategy')
    mounts = tpl['Mounts']
    need(len(mounts) == 1 and mounts[0]['Type'] == 'bind' and mounts[0]['Destination'] == '/access-logs', 'template_mounts')
    need(subprocess.run(['docker', 'container', 'inspect', C['app']], capture_output=True).returncode != 0, 'name_free')
    need(not listening(8080), 'port_8080_free')
    need(shutil.disk_usage('/home/ec2-user').free > 1 << 30, 'disk_free_1GiB')

    base = pathlib.Path(C['remote_session_dir'])
    live = pathlib.Path(C['jfr_live_dir'])
    need(not base.exists() and not live.exists(), 'new_dirs_absent')
    base.parent.mkdir(mode=0o700, exist_ok=True)
    base.mkdir(mode=0o750)
    live.mkdir(mode=0o755)
    access = base / 'access'
    access.mkdir(mode=0o755)
    settings = base / 'jfr-settings'
    settings.mkdir(mode=0o750)
    (settings / 'cpu-lite.jfc').write_text(C['jfc_xml'])

    args = ['docker', 'create', '--name', C['app'], '--pull=never', '--entrypoint', 'java',
            '--workdir', tpl['Config']['WorkingDir'], '--network', 'bridge',
            '-v', '%s:/diagnostics:ro' % settings, '-v', '%s:/access-logs' % access,
            '-v', '%s:/jfr-repo' % live]
    for port, bindings in tpl['HostConfig']['PortBindings'].items():
        for b in bindings:
            args += ['-p', (b['HostIp'] + ':' if b['HostIp'] else '') + b['HostPort'] + ':' + port]
    if tpl['Config']['User']:
        args += ['--user', tpl['Config']['User']]
    fd, envfile = tempfile.mkstemp(prefix='dbc-env-')
    try:
        os.chmod(envfile, 0o600)
        with os.fdopen(fd, 'w') as f:
            f.write('\n'.join(tpl['Config']['Env']) + '\n')
        run(args + ['--env-file', envfile, tpl['Image'], C['jfr_extra_option'], C['jfr_start_option']]
            + tpl['Config']['Entrypoint'][1:] + tpl['Config']['Cmd'])
    finally:
        os.unlink(envfile)

    new = json.loads(run(['docker', 'inspect', C['app']]))[0]
    need(new['Image'] == tpl['Image'] and new['Config']['Env'] == tpl['Config']['Env'], 'clone_image_env')
    need(new['Args'] == [C['jfr_extra_option'], C['jfr_start_option']] + tpl['Args'], 'clone_args')
    need(new['HostConfig']['PortBindings'] == tpl['HostConfig']['PortBindings'], 'clone_ports')
    need({m['Destination'] for m in new['Mounts']} == {'/diagnostics', '/access-logs', '/jfr-repo'}, 'clone_mounts')

    started = time.time()
    run(['docker', 'start', C['app']])
    try:
        for _ in range(C.get('health_wait_seconds', 150)):
            try:
                if json.load(urllib.request.urlopen('http://127.0.0.1:8080/actuator/health', timeout=2))['status'] == 'UP':
                    break
            except Exception:
                pass
            time.sleep(1)
        else:
            raise RuntimeError('not_healthy_in_time')
        healthy_after = round(time.time() - started, 1)
        fresh = json.loads(run(['docker', 'inspect', C['app']]))[0]
        need('Started recording' in run(['docker', 'logs', C['app']], 30), 'jfr_started_log')
        time.sleep(3)
        files = []
        for p in sorted(live.rglob('*')):
            st = p.stat()
            files.append({'path': str(p.relative_to(live)), 'bytes': st.st_size, 'mode': oct(st.st_mode & 0o777),
                          'uid': st.st_uid, 'readable_by_me': os.access(p, os.R_OK)})
        state = {'ok': True, 'app': C['app'], 'image': fresh['Image'], 'pid': fresh['State']['Pid'],
                 'started_at': fresh['State']['StartedAt'], 'healthy_after_seconds': healthy_after,
                 'jfr_repository_files': files, 'my_uid': os.getuid(), 'args': new['Args']}
    except Exception:
        subprocess.run(['docker', 'stop', '--time', '20', C['app']], capture_output=True, timeout=40)  # 새 컨테이너만
        raise
    (base / 'app-start-check.json').write_text(json.dumps(state, indent=2))
    print(json.dumps(state))


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print(json.dumps({'ok': False, 'error': str(exc)[:300]}))
        sys.exit(1)
