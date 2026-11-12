#!/usr/bin/env python3
"""이미 끝난 DB Only C 실행의 SQL·로그·수집 원본을 보존한다. 부하를 시작하지 않는다."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
# 단계 시연용 main RPS (run-db-c-d.py와 같은 값). 총량은 main 10,000 / warmup 1,000 그대로.
DEMO_RPS = (100, 150, 200, 300, 400, 500, 600)

# 원격 앱의 환경값은 메모리에서만 읽는다. docker inspect 원문·비밀번호는 출력하지 않는다.
REMOTE = r'''import hashlib,json,os,pathlib,subprocess,sys,urllib.parse
os.umask(0o077)
out,app,crew,join,expected,host,port,database,user,sql=sys.argv[1:]
base=pathlib.Path(out); evidence=base/'finish-c-evidence'
if not base.is_dir(): raise SystemExit('Remote result directory missing')
evidence.mkdir(mode=0o700,exist_ok=False)
result={'errors':[],'sql_checks':{},'active_collectors':[]}
def save(name,text):
 with (evidence/name).open('x') as f: f.write(text)
try:
 p=subprocess.run(['docker','inspect',app],capture_output=True,text=True,check=True,timeout=15)
 info=json.loads(p.stdout)[0]
 env=dict(item.split('=',1) for item in info['Config'].get('Env',[]) if '=' in item)
 url=urllib.parse.urlparse(env['DB_URL'].removeprefix('jdbc:'))
 if (url.hostname,url.port or 5432,url.path.lstrip('/'),env.get('DB_USERNAME')) != (host,int(port),database,user):
  raise ValueError('Unexpected database identity')
 pg=os.environ.copy()
 pg.update(PGHOST=host,PGPORT=port,PGDATABASE=database,PGUSER=user,PGPASSWORD=env['DB_PASSWORD'],
           PGSSLMODE='require',PGCONNECT_TIMEOUT='5',
           PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=10000')
 p=subprocess.run(['psql','-X','-w','-At','-F','\t','-v','ON_ERROR_STOP=1',
                   '-v','crew_id='+crew,'-v','k6_201='+join,'-v','expected_total='+expected,
                   '-f',sql],env=pg,capture_output=True,text=True,timeout=25)
 save('sql-after.txt',p.stdout+p.stderr);result['sql_exit']=p.returncode
 for line in p.stdout.splitlines():
  fields=line.split('\t')
  if len(fields)>=3 and fields[0].startswith(('I1_','I2_','I3_','I4_','I5_','I6_','I7_')):
   result['sql_checks'][fields[0]]=fields[1]
except Exception as exc: result['errors'].append('sql:'+type(exc).__name__)
try:
 with (evidence/'app.log').open('xb') as stream:
  p=subprocess.run(['docker','logs','--timestamps',app],stdout=stream,stderr=subprocess.STDOUT,timeout=30)
 result['app_log_exit']=p.returncode
except Exception as exc: result['errors'].append('logs:'+type(exc).__name__)
for name in ['poll-db','poll-lock','mpstat','vmstat','tcp-collect']:
 try:
  pidfile=base/(name+'.pid')
  if pidfile.exists():
   pid=pidfile.read_text().split()[0]
   if not pid.isdigit(): raise ValueError('Invalid PID')
   p=subprocess.run(['ps','-p',pid,'-o','args='],capture_output=True,text=True,timeout=5)
   if p.returncode==0 and name in p.stdout: result['active_collectors'].append(name)
 except Exception as exc: result['errors'].append('collector:'+name+':'+type(exc).__name__)
save('finish.json',json.dumps(result,indent=2)+'\n')
manifest=[]
for path in sorted(base.rglob('*')):
 if path.is_file() and not path.is_symlink():
  h=hashlib.sha256()
  with path.open('rb') as stream:
   for chunk in iter(lambda:stream.read(1024*1024),b''): h.update(chunk)
  manifest.append({'path':str(path.relative_to(base)),'sha256':h.hexdigest()})
result['manifest']=manifest
print(json.dumps(result))
'''


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def capture(command, timeout, env=None):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout, env=env)
        return result.returncode, result.stdout, result.stderr
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 124, '', type(exc).__name__


def finish(config):
    os.umask(0o077)
    card = json.loads(Path(config).read_text())
    run, phase, crew = card['run_id'], card.get('phase', 'main'), card['crew_id']
    if phase not in ('main', 'warmup') or not re.fullmatch(r'\d{8}-\d{6}-db-[1-9][0-9]*-[0-9a-f]{4}', run):
        raise ValueError('Invalid C run/phase')
    rate = int(run.split('-')[3])
    if not 1 <= rate <= 1000000 or (phase == 'warmup' and rate != 50):
        raise ValueError('C run rate does not match phase')
    if not re.fullmatch(r'dbb-[A-Za-z0-9-]{1,31}', crew):
        raise ValueError('Invalid crew ID')
    out = Path(card['out_dir'])
    if out != ROOT / 'load-test/results/raw' / run[:8] / run:
        raise ValueError('Unexpected local result directory')
    summary_name = 'warmup-c-summary.json' if phase == 'warmup' else 'k6-c-summary.json'
    execution_name = 'warmup-c-execution.json' if phase == 'warmup' else 'c-execution.json'
    summary = json.loads((out / summary_name).read_text())['dbc']
    execution = json.loads((out / execution_name).read_text())
    for data in (summary, execution):
        if data.get('run_id') != run or data.get('crew_id') != crew:
            raise ValueError('C run/crew mismatch; refusing remote operations')
    if summary.get('warmup') is not (phase == 'warmup') or execution.get('phase') != phase or execution.get('version') != 'C':
        raise ValueError('C phase/version mismatch')
    join = summary['counts']['join_201']
    if type(join) is not int or join < 0:
        raise ValueError('Invalid observed success count')
    for key in ('db_host', 'db_name', 'db_user', 'app', 'verify_sql'):
        if not isinstance(card.get(key), str) or not card[key]:
            raise ValueError('Required configuration field: ' + key)
    if not card['verify_sql'].startswith('/'):
        raise ValueError('verify_sql must be an absolute remote path')
    port = card.get('db_port', 5432)
    if type(port) is not int or not 1 <= port <= 65535:
        raise ValueError('Invalid DB port')
    remote_root = os.environ['EC2_RESULTS_ROOT'].rstrip('/')
    if not remote_root.startswith('/'):
        raise ValueError('Remote result root must be absolute')
    remote_out = f'{remote_root}/{run[:8]}/{run}' + ('/warmup' if phase == 'warmup' else '')
    ssh_options = ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=5',
                   '-i', os.environ['APP_SSH_KEY']]
    target = os.environ['APP_SSH_TARGET']
    # 실행 시각을 검증한 뒤 새 결과 폴더를 만든다.
    start = execution.get('start', {}).get('ms')
    stop_end = execution.get('stop_end', {}).get('ms')
    if type(start) is not int or type(stop_end) is not int or stop_end < start:
        raise ValueError('Missing execution interval')
    evidence, originals, promout = out / 'finish-c-local', out / 'remote-originals-c', out / 'prom-c'
    if any(path.exists() for path in (evidence, originals, promout)):
        raise ValueError('Finish destination exists; do not overwrite or rerun automatically')
    evidence.mkdir(mode=0o700)
    expected = card.get('expected_total', 100 if phase == 'warmup' else 1000)
    total = card.get('total', 1000 if phase == 'warmup' else 10000)
    if type(expected) is not int or not 1 <= expected <= 1000 or type(total) is not int or total < expected - 1 or total % rate:
        raise ValueError('Invalid total/capacity')
    remote_command = shlex.join(['python3', '-c', REMOTE, remote_out, card['app'], crew, str(join),
                                 str(expected), card['db_host'], str(port), card['db_name'],
                                 card['db_user'], card['verify_sql']])
    remote_rc, stdout, stderr = capture(['ssh', *ssh_options, target, remote_command], 100)
    (evidence / 'remote.stdout.txt').write_text(stdout)
    (evidence / 'remote.stderr.txt').write_text(stderr)
    try:
        remote = json.loads(stdout.strip().splitlines()[-1])
    except (ValueError, IndexError):
        remote = {}
    # SQL 검증에 실패해도 원본 회수를 시도하고 기존 자료는 삭제하지 않는다.
    scp_rc, stdout, stderr = capture(['scp', '-r', *ssh_options, target + ':' + remote_out, str(originals)], 180)
    (evidence / 'scp.stdout.txt').write_text(stdout)
    (evidence / 'scp.stderr.txt').write_text(stderr)
    if originals.exists():
        for path in originals.rglob('*'):
            if not path.is_symlink():
                path.chmod(0o700 if path.is_dir() else 0o600)
        originals.chmod(0o700)
    manifest = remote.get('manifest', [])
    hashes_match = bool(manifest)
    for entry in manifest:
        relative = Path(entry['path'])
        if relative.is_absolute() or '..' in relative.parts:
            hashes_match = False
            continue
        path = originals / relative
        if not path.is_file() or path.is_symlink() or sha256(path) != entry['sha256']:
            hashes_match = False
    (evidence / 'remote-sha256-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    # VU 초기화 전부터 후처리 시점까지 보존한다. 요청 투입 10초와는 구분한다.
    end = time.time_ns() // 1000000
    promout.mkdir(mode=0o700)
    if card.get('raw_metrics_dir'):
        raw_dir = Path(card['raw_metrics_dir'])
        if raw_dir != out / 'raw-metrics':
            raise ValueError('Unexpected raw metrics directory')
        samples = [json.loads(line) for line in (raw_dir / 'index.jsonl').read_text().splitlines()]
        successful = [sample for sample in samples if 'file' in sample and (raw_dir / sample['file']).is_file()]
        scenario_ms = summary.get('scenario_start_epoch_ms')
        covered = (type(scenario_ms) is int and bool(successful) and
                   successful[0]['ms'] <= scenario_ms and
                   successful[-1]['ms'] >= execution['k6_end']['ms'])
        prom_rc = 0 if covered and len(successful) == len(samples) else 2
        stdout = json.dumps({'source': 'raw actuator scrapes', 'samples': len(samples),
                             'successful': len(successful), 'covers_load': covered})
        stderr = ''
    else:
        prom_rc, stdout, stderr = capture(['bash', str(ROOT / 'load-test/collectors/prom-export.sh')], 180,
                                         {**os.environ, 'START_MS': str(start), 'END_MS': str(end), 'OUT_DIR': str(promout)})
    (evidence / 'prom.stdout.txt').write_text(stdout)
    (evidence / 'prom.stderr.txt').write_text(stderr)
    required = {'I1_no_overflow', 'I2_counter_match', 'I3_no_duplicate', 'I4_one_leader',
                'I5_pool_only', 'I6_full_info', 'I7_k6_match'}
    checks = remote.get('sql_checks', {})
    sql_ok = remote.get('sql_exit') == 0 and all(checks.get(key) == 'OK' for key in required)
    collected = (remote_rc == 0 and remote.get('app_log_exit') == 0 and not remote.get('errors') and
                 remote.get('active_collectors') == [] and scp_rc == 0 and hashes_match and prom_rc in (0, 3))
    load_ok = (execution.get('k6_exit') == 0 and execution.get('stop_exit') == 0 and
               summary.get('load_and_response_checks_passed') is True and
               summary.get('total') == total and
               summary.get('target_rps') == rate and
               join == expected - 1)
    passed = load_ok and sql_ok and collected
    status = {'run_id': run, 'crew_id': crew, 'phase': phase,
              'load_passed': load_ok, 'sql_passed': sql_ok, 'evidence_collection_passed': collected,
              'remote_exit': remote_rc, 'scp_exit': scp_rc, 'prom_exit': prom_rc,
              'remote_hashes_match': hashes_match, 'sql_checks': checks,
              'active_collectors': remote.get('active_collectors'),
              'prom_start_ms': start, 'prom_end_ms': end,
              'prom_scrape_quality': 'not evaluated; inspect raw export',
              'classification': 'PASS_WITH_SCRAPE_REVIEW_PENDING' if passed else 'PARTIAL_OR_FAILED'}
    (evidence / 'finish-status.json').write_text(json.dumps(status, indent=2) + '\n')
    print(json.dumps(status))
    return 0 if passed else 2


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('config', help='run-db-c-d.py와 같은 READY JSON')
    raise SystemExit(finish(parser.parse_args().config))
