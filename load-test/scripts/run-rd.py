#!/usr/bin/env python3
"""Local k6 runner; same C3/k6-block arguments, automatic existing C8 stop.
Config is prepared only after the readiness gates pass. No secrets in config/output.

RD variant of ../2026-10-14-185842-db50-manual-48404/run-db50.py (copied 2026-10-14 20:29).
Only differences: RUN_ID must carry "-rd-<rps>-", and a reminder for the manual Redis-box steps
is printed at the end. k6 arguments, C8 stop call and file contracts are identical.
"""
import datetime
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import sys
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'load-test/k6/db-baseline-b.js'
KEY = Path(os.environ['APP_SSH_KEY'])
TARGET = os.environ['APP_SSH_TARGET']
BASE_URL = os.environ['BASE_URL']
REMOTE_RESULTS_ROOT = os.environ['EC2_RESULTS_ROOT'].rstrip('/')
REMOTE_COLLECTORS_DIR = os.environ['EC2_COLLECTORS_DIR'].rstrip('/')
if not REMOTE_RESULTS_ROOT.startswith('/') or not REMOTE_COLLECTORS_DIR.startswith('/'):
    raise ValueError('Remote paths must be absolute')

def stamp():
    return {'ms': time.time_ns() // 1000000,
            'kst': datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat()}

def main(config):
    c = json.loads(Path(config).read_text())
    phase = c.get('phase', 'main')
    if phase not in ('main', 'warmup'):
        raise ValueError('Invalid phase')
    tag = 'warmup-k6' if phase == 'warmup' else 'k6'
    summary = 'warmup-summary.json' if phase == 'warmup' else 'k6-summary.json'
    stop_name = 'warmup-c8-stop.log' if phase == 'warmup' else 'c8-stop.log'
    report_name = 'warmup-manual-execution.json' if phase == 'warmup' else 'manual-execution.json'
    run = c['run_id']
    rate = c.get('target_rps', 50)
    if type(rate) is not int or rate not in (50, 200):
        raise ValueError('Only RD-50/200 supported')
    if not re.fullmatch(r'\d{8}-\d{6}-rd-'+str(rate)+r'-[0-9a-f]{4}', run):
        raise ValueError('RUN_ID and target_rps disagree')
    # DB-50 historical configs retain their exact settings. DB-200 requires explicit values.
    pre = c['pre_vus'] if rate == 200 else c.get('pre_vus', 150)
    maximum = c['max_vus'] if rate == 200 else c.get('max_vus', 250)
    if type(pre) is not int or type(maximum) is not int or not 0 < pre <= maximum:
        raise ValueError('Invalid VU allocation')
    if not re.fullmatch(r'dbb-[A-Za-z0-9-]{1,31}', c['crew_id']):
        raise ValueError('Invalid crew ID')
    offset = int(c['clock_offset_ms'])
    out = Path(c['out_dir'])
    if out != ROOT / 'load-test/results/raw' / run[:8] / run:
        raise ValueError('Unexpected result directory')
    if not out.is_dir() or not c.get('ready'):
        raise ValueError('READY configuration required')
    tokens = Path(c['tokens_file'])
    if not tokens.is_file() or tokens.stat().st_mode & 0o077:
        raise ValueError('Private token file required')
    names = [tag+'-console.log', tag+'-run.txt', tag+'.json', summary, stop_name, report_name]
    if any((out / n).exists() for n in names):
        raise ValueError('Existing result files: do not overwrite/retry this run')
    os.umask(0o077)
    (out / ('.'+phase+'-manual-run-claimed')).mkdir(mode=0o700)  # one run, never reuse
    cmd = ['k6', 'run', '--out', 'json=' + str(out / (tag+'.json'))]
    values = {'BASE_URL': BASE_URL, 'CREW_ID': c['crew_id'],
              'TARGET_RPS': str(rate), 'TOTAL': '1000' if phase == 'warmup' else '10000',
              'PRE_VUS': str(pre), 'MAX_VUS': str(maximum),
              'WARMUP': '1' if phase == 'warmup' else '0',
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
    report = {'run_id': run, 'crew_id': c['crew_id'], 'start': start, 'k6_end': end,
              'k6_exit': rc, 'signals': interrupted, 'stop_start': stop_start,
              'stop_end': finish, 'stop_exit': stop_rc, 'errors': errors,
              'k6_command': cmd, 'stop_command': ssh,
              'phase': phase, 'summary_present': (out / summary).is_file()}
    with (out / report_name).open('x') as f:
        json.dump(report, f, indent=2)
    print(f'k6 exit={rc}; C8 stop exit={stop_rc}; results={out}', flush=True)
    if stop_rc not in (0, 3):
        print('C8 stop needs investigation. Do not repeat this run or blindly retry stop.')
    print('REMINDER (manual, Redis arm): stop Redis-box mpstat/vmstat, then redis-info-after, '
          'redis-final, sql-after-drain. C8 stop only waits for the drain; it does not do these.', flush=True)
    return rc if rc else (0 if stop_rc == 0 else stop_rc)

if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
