#!/usr/bin/env python3
"""테스트 EC2에서 JFR 저장소 경로를 추가한 테스트 앱을 새 컨테이너로 한 번 기동한다.

정지 상태의 템플릿 컨테이너 설정(이미지·환경·인자·포트)을 복제하고, 다른 컨테이너는 중지·시작·삭제하지 않는다.
(지난 operation-start-jfr-app-remote.py는 실패 시 원본을 다시 켰다 — 여기서는 하지 않는다.)
사용법: ssh … python3 - '<JSON 설정>' < start-test-app-remote.py   (JSON 키는 아래 C 참조, 비밀값 없음)
선택 키: accept_count(정수 1~65535) / redis / extra_jvm_options(힙·GC 알고리즘 옵션은 거부) / new_image{ref,id} / extra_env_file(EC2 안의 비공개 파일 경로, 값은 출력하지 않음)
"""
import json
import os
import pathlib
import re
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


# 힙 크기·GC 알고리즘을 바꾸는 옵션은 이 도구로 넣을 수 없다 (승인된 계측 옵션만 허용)
FORBIDDEN_JVM_PREFIXES = ('-Xmx', '-Xms', '-Xss', '-XX:+Use', '-XX:-Use', '-XX:MaxRAM', '-XX:InitialRAM',
                          '-XX:MaxHeap', '-XX:InitialHeap', '-XX:MinHeap', '-XX:NewSize', '-XX:MaxNewSize')


def validate_jvm_options(options):
    need(isinstance(options, list) and all(isinstance(o, str) and o.startswith('-X') for o in options),
         'extra_jvm_options_form')
    need(not any(o.startswith(FORBIDDEN_JVM_PREFIXES) for o in options), 'extra_jvm_options_no_heap_or_gc_change')
    return options


ACCEPT_COUNT_UNSET = object()


def swap_args(args, redis, accept_count=ACCEPT_COUNT_UNSET):
    """템플릿 순서를 유지해 accept-count를 교체한 뒤 기존 Redis 전환을 적용한다."""
    if accept_count is not ACCEPT_COUNT_UNSET:
        need(type(accept_count) is int and 1 <= accept_count <= 65535, 'accept_count_range')
        prefix = '--server.tomcat.accept-count='
        need(sum(a.startswith(prefix) for a in args) == 1, 'accept_count_exactly_one')
        args = [prefix + str(accept_count) if a.startswith(prefix) else a for a in args]
    if not redis:
        return args
    return [a for a in args if not a.startswith('--triagain.crew.lock-strategy=')] + [
        '--triagain.crew.lock-strategy=REDIS_ASYNC', '--spring.data.redis.host=' + redis['host'],
        '--spring.data.redis.port=' + str(redis['port']),
        '--triagain.crew.redis.namespace=' + redis['namespace'],
        '--triagain.crew.redis.run-id=' + redis['run_id']]


def env_names(lines):
    return sorted(x.split('=', 1)[0] for x in lines if '=' in x)


def read_extra_env(path, existing_names):
    """비공개 env 파일(소유자만 접근)을 읽는다. 값은 어디에도 출력하지 않고 오류에는 이름·조건만 쓴다."""
    st = os.stat(path)
    need(st.st_mode & 0o077 == 0 and st.st_size < 4096, 'extra_env_file_private')
    lines = [x for x in pathlib.Path(path).read_text().splitlines() if x.strip()]
    need(all(re.fullmatch(r'[A-Z][A-Z0-9_]*=\S.*', x) for x in lines), 'extra_env_line_form')
    names = [x.split('=', 1)[0] for x in lines]
    need(len(set(names)) == len(names) and not set(names) & set(existing_names), 'extra_env_names_unique')
    return lines


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
    redis = C.get('redis')  # 선택(REDIS_ASYNC 전환): lock-strategy 인자 교체 + redis 인자 4개 추가. 없으면 템플릿 그대로
    swap_args(tpl['Config']['Cmd'], redis, C.get('accept_count', ACCEPT_COUNT_UNSET))
    swap_args(tpl['Args'], redis, C.get('accept_count', ACCEPT_COUNT_UNSET))
    extra_opts = validate_jvm_options(C.get('extra_jvm_options', []))  # 선택: 승인된 계측 옵션(예: GC 로그)을 -jar 앞에 추가
    extra_env = read_extra_env(C['extra_env_file'], env.keys()) if C.get('extra_env_file') else []  # 선택: 비공개 env 추가
    new_image = C.get('new_image')  # 선택: {'ref','id'} — 템플릿 이미지 대신 이 이미지로 컨테이너를 만든다
    image_ref, image_id = tpl['Image'], tpl['Image']
    if new_image:
        img = json.loads(run(['docker', 'image', 'inspect', new_image['ref']]))[0]
        need(img['Id'] == new_image['id'] and img['Architecture'] == 'amd64' and img['Os'] == 'linux', 'new_image_id')
        need(img['Config']['Entrypoint'] == tpl['Config']['Entrypoint']
             and img['Config']['WorkingDir'] == tpl['Config']['WorkingDir'], 'new_image_config')
        image_ref, image_id = new_image['ref'], new_image['id']

    def swap(args):
        return swap_args(args, redis, C.get('accept_count', ACCEPT_COUNT_UNSET))

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
            f.write('\n'.join(tpl['Config']['Env'] + extra_env) + '\n')
        run(args + ['--env-file', envfile, image_ref, C['jfr_extra_option'], C['jfr_start_option']] + extra_opts
            + tpl['Config']['Entrypoint'][1:] + swap(tpl['Config']['Cmd']))
    finally:
        os.unlink(envfile)

    new = json.loads(run(['docker', 'inspect', C['app']]))[0]
    need(new['Image'] == image_id and sorted(new['Config']['Env']) == sorted(tpl['Config']['Env'] + extra_env),
         'clone_image_env')
    need(env_names(new['Config']['Env']) == sorted(env_names(tpl['Config']['Env']) + env_names(extra_env)),
         'clone_env_names')
    need(new['Args'] == [C['jfr_extra_option'], C['jfr_start_option']] + extra_opts + swap(tpl['Args']), 'clone_args')
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
                 'jfr_repository_files': files, 'my_uid': os.getuid(), 'args': new['Args'],
                 'extra_jvm_options': extra_opts, 'extra_env_names': env_names(extra_env)}
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
