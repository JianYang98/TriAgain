#!/usr/bin/env python3
"""맥 감시기: k6 콘솔 로그의 `SCENARIO_START_MS=<epoch ms>` 한 줄을 기다렸다가 EC2 시작 파일에 한 번 전달한다.

그 값은 db-baseline-c.js 가 iteration 0 에서 찍는 exec.scenario.startTime(= 요약의 scenario_start_epoch_ms 와 같은 값)이며,
run-db-c.py 가 k6 출력을 한 줄씩 flush 해서 k6-c-console.log 에 쌓는다. EC2 예약기(dump-scheduler.py)가 이 파일을 기다린다.
전달 경로는 시작 전에 미리 연 SSH 연결(ControlMaster)을 쓴다 — 새 연결의 핸드셰이크(약 0.3초)를 피하려는 것이다.
시작 파일에는 늦음을 가르는 데 필요한 맥 쪽 시각을 함께 적는다:
  scenario_start_mac_ms · offset_ms(EC2−맥) · ec2_start_ms · watcher_seen_mac_ms(줄을 읽은 시각) · watcher_sent_mac_ms(전송 직전)
사용: APP_SSH_KEY·APP_SSH_TARGET 환경변수 또는 --ssh-cmd 로 접속 명령을 준다.
  python3 -I dump-start-relay.py --console-log <k6-c-console.log> --offset-ms 16 --remote-file <EC2 경로> [--check]
종료 코드: 0 전달함 · 3 시간 안에 표식이 없음 · 4 전달 실패 · 1 입력 오류
"""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import time

MARKER = re.compile(r'SCENARIO_START_MS=(\d{13})(?:\.\d+)?')


def now_ms():
    return time.time_ns() // 1000000


class Log:
    def __init__(self, path):
        self.handle = path.open('x')

    def write(self, **fields):
        self.handle.write(json.dumps(fields, sort_keys=True) + '\n')
        self.handle.flush()


def wait_for_marker(path, wait_seconds, poll_seconds):
    """로그 파일이 생기면 처음부터 읽으며 완성된 줄에서 표식을 찾는다. (값, 읽은 시각ms) 또는 (None, None)."""
    deadline = time.time() + wait_seconds
    while time.time() < deadline and not path.exists():
        time.sleep(0.05)
    if not path.exists():
        return None, None
    pending = b''
    with path.open('rb') as handle:
        while time.time() < deadline:
            chunk = handle.read()
            if chunk:
                pending += chunk
                *lines, pending = pending.split(b'\n')
                for line in lines:
                    match = MARKER.search(line.decode('utf-8', errors='replace'))
                    if match:
                        return int(match.group(1)), now_ms()
            else:
                time.sleep(poll_seconds)
    return None, None


def send(ssh_cmd, remote_file, payload, log, attempt):
    quoted = shlex.quote(remote_file)
    remote = 'umask 077; test ! -e %s && cat > %s.tmp && mv %s.tmp %s' % (quoted, quoted, quoted, quoted)
    begin = now_ms()
    result = subprocess.run(ssh_cmd + [remote], input=json.dumps(payload), capture_output=True, text=True, timeout=30)
    log.write(event='ssh', attempt=attempt, ssh_start_ms=begin, ssh_end_ms=now_ms(), rc=result.returncode,
              stderr=result.stderr.strip()[:200])
    return result.returncode


def open_control(options, target, control_path):
    """미리 연 SSH 연결 — ControlPersist 로 백그라운드에 남는다. 목적지는 반드시 옵션 뒤에 둔다. 전송용 접속 명령을 돌려준다."""
    master = ['ssh', '-o', 'ControlMaster=yes', '-o', 'ControlPath=' + control_path, '-o', 'ControlPersist=1800']
    subprocess.run(master + options + ['-N', '-f', target], check=True, timeout=30)
    return ['ssh', '-o', 'ControlPath=' + control_path] + options + [target]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--console-log', required=True)
    parser.add_argument('--offset-ms', type=int, required=True, help='EC2 시각 − 맥 시각 (clock-check.sh)')
    parser.add_argument('--remote-file', required=True)
    parser.add_argument('--ssh-cmd', help='접속 명령(공백 구분). 주면 ControlMaster 를 쓰지 않는다(테스트용)')
    parser.add_argument('--log', help='감시기 기록(JSONL, 새로 만든다). 기본: 콘솔 로그 옆 dump-relay.jsonl')
    parser.add_argument('--wait-seconds', type=float, default=900)
    parser.add_argument('--poll-ms', type=float, default=5)
    parser.add_argument('--check', action='store_true', help='연결만 확인하고 끝낸다(표식을 기다리지 않음)')
    args = parser.parse_args(argv)
    os.umask(0o077)
    console = Path(args.console_log)
    log = Log(Path(args.log) if args.log else console.with_name('dump-relay.jsonl'))
    control_path, ssh_cmd = None, None
    try:
        if args.ssh_cmd:
            ssh_cmd = shlex.split(args.ssh_cmd)
        else:
            options = ['-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=5',
                       '-i', os.environ['APP_SSH_KEY']]
            # Unix 소켓 경로는 macOS 에서 104자 한도 — TMPDIR 이 길 수 있어 짧은 /tmp 아래에 만든다(ssh 가 접미 17자를 더 붙인다).
            control_path = tempfile.mkdtemp(prefix='dbb-relay-', dir='/tmp') + '/cm'
            if len(control_path) > 80:
                raise ValueError('ControlPath 가 너무 길다: %d자' % len(control_path))
            ssh_cmd = open_control(options, os.environ['APP_SSH_TARGET'], control_path)
        begin = now_ms()
        alive = subprocess.run(ssh_cmd + ['true'], capture_output=True, timeout=30).returncode
        log.write(event='connection_check', rc=alive, rtt_ms=now_ms() - begin, offset_ms=args.offset_ms)
        if args.check or alive:
            return alive
        log.write(event='waiting', console_log=str(console), at_ms=now_ms())
        start_mac, seen = wait_for_marker(console, args.wait_seconds, args.poll_ms / 1000)
        if start_mac is None:
            log.write(event='no_marker', at_ms=now_ms())
            return 3
        log.write(event='marker_seen', scenario_start_mac_ms=start_mac, watcher_seen_mac_ms=seen,
                  detect_lag_ms=seen - start_mac)
        for attempt in (1, 2):
            payload = {'scenario_start_mac_ms': start_mac, 'offset_ms': args.offset_ms,
                       'ec2_start_ms': start_mac + args.offset_ms, 'watcher_seen_mac_ms': seen,
                       'watcher_sent_mac_ms': now_ms()}
            rc = send(ssh_cmd, args.remote_file, payload, log, attempt)
            if rc != 255:  # 255 = ssh 자체 실패만 한 번 더 시도한다. 원격에서 거절(이미 있음 등)은 되풀이하지 않는다.
                break
        log.write(event='done', delivered=rc == 0, rc=rc)
        return 0 if rc == 0 else 4
    finally:
        if control_path:
            subprocess.run(['ssh', '-o', 'ControlPath=' + control_path, '-O', 'exit', 'x'], capture_output=True, timeout=10)
            try:
                os.rmdir(os.path.dirname(control_path))
            except OSError:
                pass


if __name__ == '__main__':
    sys.exit(main())
