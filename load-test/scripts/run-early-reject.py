#!/usr/bin/env python3
"""DB Only C 실행기: k6 종료 직후 기존 C8 수집기를 종료하고 결과를 보존한다."""
import datetime
import json
import os
from pathlib import Path
import re
import resource
import shlex
import signal
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'load-test/k6/db-baseline-c-d.js'
KEY = Path(os.environ['APP_SSH_KEY'])
TARGET = os.environ['APP_SSH_TARGET']
BASE_URL = os.environ['BASE_URL']
# 단계 시연용 main RPS. TOTAL은 그대로 10,000이라 300·600은 투입 시간이 나누어떨어지지 않는다 (k6 쪽 ALLOW_FRACTIONAL_ARRIVAL).
DEMO_RPS = (100, 150, 200, 300, 400, 500, 600)
REMOTE_RESULTS_ROOT = os.environ['EC2_RESULTS_ROOT'].rstrip('/')
REMOTE_COLLECTORS_DIR = os.environ['EC2_COLLECTORS_DIR'].rstrip('/')
if not REMOTE_RESULTS_ROOT.startswith('/') or not REMOTE_COLLECTORS_DIR.startswith('/'):
    raise ValueError('Remote paths must be absolute')

def stamp():
    return {'ms': time.time_ns() // 1000000,
            'kst': datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat()}

def run_phase(config, check_only=False):
    c = json.loads(Path(config).read_text())
    phase = c.get('phase', 'main')
    if phase not in ('main', 'warmup'):
        raise ValueError('Invalid phase')
    tag = 'warmup-k6-c' if phase == 'warmup' else 'k6-c'
    summary = 'warmup-c-summary.json' if phase == 'warmup' else 'k6-c-summary.json'
    stop_name = 'warmup-c8-stop.log' if phase == 'warmup' else 'c8-stop.log'
    report_name = 'warmup-c-execution.json' if phase == 'warmup' else 'c-execution.json'
    run = c['run_id']
    rate = c.get('target_rps', 50 if phase == 'warmup' else 1000)
    if type(rate) is not int or not 1 <= rate <= 1000000 or (phase == 'warmup' and rate != 50):
        raise ValueError('RPS must be a positive integer <= 1000000; warmup uses 50')
    total = c.get('total', 1000 if phase == 'warmup' else 10000)
    expected = c.get('expected_total', 100 if phase == 'warmup' else 1000)
    if type(total) is not int or total < 1 or total % rate:
        raise ValueError('total must be positive and divisible by RPS')
    if type(expected) is not int or not 1 <= expected <= 1000 or total < expected - 1:
        raise ValueError('Invalid expected_total')
    if c.get('arrival_seconds', total // rate) != total // rate:
        raise ValueError('arrival_seconds and total/RPS disagree')
    if not re.fullmatch(r'\d{8}-\d{6}-db-'+str(rate)+r'-[0-9a-f]{4}', run):
        raise ValueError('RUN_ID and target_rps disagree')
    pre = c.get('pre_vus', 150 if phase == 'warmup' else 10000)
    maximum = c.get('max_vus', 250 if phase == 'warmup' else 10000)
    timeout = c.get('request_timeout_seconds', 60)
    limit = c.get('validated_vu_limit', 10000)
    if type(limit) is not int or not 1 <= limit <= 1000000:
        raise ValueError('Invalid validated VU limit')
    if type(pre) is not int or type(maximum) is not int or not 0 < pre <= maximum <= limit:
        raise ValueError('VU allocation exceeds validated_vu_limit')
    if type(timeout) is not int or not 1 <= timeout <= 600:
        raise ValueError('Request timeout must be integer seconds, 1..600')
    if not re.fullmatch(r'dbb-[A-Za-z0-9-]{1,31}', c['crew_id']):
        raise ValueError('Invalid crew ID')
    offset = c['clock_offset_ms']
    if type(offset) is not int:
        raise ValueError('Clock offset must be an integer')
    out = Path(c['out_dir'])
    if out != ROOT / 'load-test/results/raw' / run[:8] / run:
        raise ValueError('Unexpected result directory')
    if not out.is_dir() or (not check_only and c.get('ready') is not True):
        raise ValueError('READY configuration required')
    tokens = Path(c['tokens_file'])
    if not tokens.is_absolute() or not tokens.is_file() or tokens.stat().st_mode & 0o077:
        raise ValueError('Private token file required')
    names = [tag+'-console.log', tag+'-run.txt', tag+'.json', summary, stop_name, report_name]
    if any((out / n).exists() for n in names):
        raise ValueError('Existing result files: do not overwrite/retry this run')
    if (out / ('.'+phase+'-c-run-claimed')).exists():
        raise ValueError('C run already claimed; use a new run')
    if check_only:
        print(f'C config PASS: phase={phase}, RPS={rate}, total={total}, capacity={expected}, arrival={total // rate}s, VU={pre}/{maximum}, timeout={timeout}s')
        return 0
    os.umask(0o077)
    (out / ('.'+phase+'-c-run-claimed')).mkdir(mode=0o700)  # one run, never reuse
    cmd = ['k6', 'run', '--out', 'json=' + str(out / (tag+'.json'))]
    values = {'BASE_URL': BASE_URL, 'CREW_ID': c['crew_id'],
              'TARGET_RPS': str(rate), 'TOTAL': str(total),
              'PRE_VUS': str(pre), 'MAX_VUS': str(maximum),
              'WARMUP': '1' if phase == 'warmup' else '0',
              'REQ_TIMEOUT_SECONDS': str(timeout),
              'ALLOW_FRACTIONAL_ARRIVAL': '0',
              'TOKENS_FILE': str(tokens), 'RUN_ID': run, 'OUT_DIR': str(out)}
    for k, v in values.items():
        cmd += ['-e', k + '=' + v]
    cmd.append(str(SCRIPT))
    interrupted = []
    child = None

    def interrupt(sig, frame):
        interrupted.append(sig)
        # Signal k6 only; preserve the log reader and the later stop operation.
        if child is not None and child.poll() is None:
            child.send_signal(sig)

    signal.signal(signal.SIGINT, interrupt)
    signal.signal(signal.SIGTERM, interrupt)
    os.umask(0o077)
    start = stamp()
    rc = 127
    errors = []
    with (out / (tag+'-console.log')).open('x') as log:
        try:
            child = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                     start_new_session=True)
            def copy_output():
                for raw in iter(child.stdout.readline, b''):
                    text = raw.decode('utf-8', errors='replace')
                    log.write(text); log.flush()
                    try:
                        sys.stdout.write(text); sys.stdout.flush()
                    except (BrokenPipeError, OSError):
                        pass  # terminal closed: still preserve file output
            reader = threading.Thread(target=copy_output)
            reader.start()
            raw_rc = child.wait()
            end = stamp()  # immediately after k6 exits, before SSH
            rc = raw_rc if raw_rc >= 0 else 128 - raw_rc
            reader.join()
        except OSError as e:
            end = stamp()
            errors.append(type(e).__name__)
    with (out / (tag+'-run.txt')).open('x') as f:
        f.write(f'tag={tag} warmup={int(phase=="warmup")} run_id={run} crew={c["crew_id"]} exit={rc} '
                f'start_ms={start["ms"]} end_ms={end["ms"]} '
                f'start_local={start["kst"]} end_local={end["kst"]}\n')
    remote_out = f'{REMOTE_RESULTS_ROOT}/{run[:8]}/{run}'
    if phase == 'warmup':
        remote_out += '/warmup'
    remote = ' '.join(f'{k}={shlex.quote(str(v))}' for k, v in {
        'RUN_ID': run, 'OUT_DIR': remote_out, 'K6_END_MS': end['ms'],
        'CLOCK_OFFSET_MS': offset}.items()) + ' ' + shlex.quote(REMOTE_COLLECTORS_DIR + '/box-app-stop.sh')
    ssh = ['ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
           '-o', 'ConnectTimeout=5', '-o', 'ServerAliveInterval=5',
           '-o', 'ServerAliveCountMax=3', '-i', str(KEY), TARGET, remote]
    # C8 owns TCP-after/tail, DB drain deadline and CPU stop; do not duplicate it.
    stop_start = stamp()
    stop_rc = 255
    with (out / stop_name).open('x') as log:
        try:
            stop = subprocess.Popen(ssh, stdout=log, stderr=subprocess.STDOUT,
                                    start_new_session=True)
            stop_rc = stop.wait()
        except OSError as e:
            errors.append('stop:' + type(e).__name__)
    finish = stamp()
    report = {'version': 'C', 'phase': phase, 'run_id': run, 'crew_id': c['crew_id'], 'start': start, 'k6_end': end,
              'k6_exit': rc, 'signals': interrupted, 'stop_start': stop_start,
              'stop_end': finish, 'stop_exit': stop_rc, 'errors': errors,
              'k6_command': cmd, 'stop_command': ssh,
              'phase': phase, 'summary_present': (out / summary).is_file()}
    with (out / report_name).open('x') as f:
        json.dump(report, f, indent=2)
    print(f'k6 exit={rc}; C8 stop exit={stop_rc}; results={out}', flush=True)
    if stop_rc not in (0, 3):
        print('C8 stop needs investigation. Do not repeat this run or blindly retry stop.')
    return rc if rc else (stop_rc if stop_rc else (0 if report['summary_present'] else 2))

def main(config, check_only=False):
    # soft limit만 변경하고 정상 종료·예외·처리 가능한 신호 뒤에는 반드시 복원한다.
    original = resource.getrlimit(resource.RLIMIT_NOFILE)
    soft, hard = original
    try:
        if soft != resource.RLIM_INFINITY and soft < 16384:
            if hard != resource.RLIM_INFINITY and hard < 16384:
                raise ValueError('File descriptor hard limit below 16384; do not start load')
            resource.setrlimit(resource.RLIMIT_NOFILE, (16384, hard))
        return run_phase(config, check_only)
    finally:
        resource.setrlimit(resource.RLIMIT_NOFILE, original)

if __name__ == '__main__':
    if len(sys.argv) not in (2, 3) or (len(sys.argv) == 3 and sys.argv[2] != '--check'):
        raise SystemExit('Usage: python3 run-db-c-d.py READY.json [--check]')
    sys.exit(main(sys.argv[1], len(sys.argv) == 3))
