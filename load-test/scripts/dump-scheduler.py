#!/usr/bin/env python3
"""EC2 예약기: 시나리오 실제 시작 시각(맥 감시기가 전달)을 기준으로 정해진 오프셋마다 앱 JVM에 SIGQUIT(스레드 덤프)를 보낸다.

지난 operation-thread-scheduler.py(오프셋 10·25초, 시작 시각을 인자로 받음)를 바탕으로 다음을 바꿨다:
  · 시작 입력: 계획 시각 인자 → 시작 파일(--start-file)을 기다린다. 파일은 dump-start-relay.py(맥)가 원자적으로 쓴다.
  · 오프셋: 기본 3000·8000ms. 목표는 항상 `ec2_start_ms + 오프셋`의 절대 시각이다. 늦게 알아도 목표를 미루지 않는다.
  · 늦음을 둘로 나눈다(전체 지연 = 전달 지연 + 실행 지연, 목표 시각 기준):
      전달 지연 delivery_late_ms  = max(0, 시작을 알게 된 시각 − 목표)   ← 맥 감시기·SSH가 늦어서 목표 전에 못 알았다
      실행 지연 execution_lag_ms  = 쏘기 직전 시각 − max(목표, 알게 된 시각) ← 알고도 깨어남/확인/실행이 늦었다
  · 신호 직전 확인은 docker 호출 없이 /proc 으로 한다(포화된 서버에서 호출 자체가 지연을 키우지 않게).
  · 전체 지연이 --max-late-ms 를 넘으면 쏘지 않는다(skipped_late_*). 일찍 쏘는 일은 없다.
사용법(EC2): python3 dump-scheduler.py --dir <빈 폴더> --app <컨테이너> --start-file <경로> [--dry-run]
종료 코드: 0 전부 처리(쏘았거나 dry-run) · 2 JVM 변경/오류 · 3 시작 신호 없음 또는 일부 건너뜀
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def now_ms():
    return time.time_ns() // 1000000


def classify(delivery_late_ms, execution_lag_ms, tolerance_ms):
    """늦음의 원인 — none | delivery | execution | both (허용 오차 tolerance_ms 초과 기준)."""
    delivery, execution = delivery_late_ms > tolerance_ms, execution_lag_ms > tolerance_ms
    return 'both' if delivery and execution else 'delivery' if delivery else 'execution' if execution else 'none'


def proc_start_ticks(pid):
    """/proc/<pid>/stat 의 시작 시각(22번째 필드). /proc 이 없으면 None — PID 재사용 감지용."""
    try:
        text = Path('/proc/%d/stat' % pid).read_text()
        return text[text.rindex(')') + 2:].split()[19]
    except (OSError, ValueError, IndexError):
        return None


def pid_unchanged(pid, start_ticks):
    if start_ticks is not None:
        return proc_start_ticks(pid) == start_ticks
    try:
        os.kill(pid, 0)
    except PermissionError:
        return True  # 다른 사용자 소유라 신호는 못 보내지만 살아 있다
    except OSError:
        return False
    return True


def sleep_until(target_ms):
    """목표 시각까지 대기 — 마지막 5ms는 짧게 돌며 확인한다."""
    while True:
        remaining = target_ms / 1000 - time.time()
        if remaining <= 0:
            return
        time.sleep(remaining - 0.005 if remaining > 0.01 else 0.0005)


class Log:
    def __init__(self, path):
        self.handle = path.open('x')

    def write(self, **fields):
        self.handle.write(json.dumps(fields, sort_keys=True) + '\n')
        self.handle.flush()


def wait_for_start(path, wait_seconds):
    """시작 파일이 생길 때까지 5ms 간격으로 확인 — 처음 본 시각(observed_ms)과 내용을 돌려준다."""
    deadline = time.time() + wait_seconds
    while time.time() < deadline:
        if path.exists():
            observed = now_ms()
            return observed, json.loads(path.read_text())
        time.sleep(0.005)
    return None, None


def one_dump(args, log, state, offset_ms):
    """덤프 한 건: 목표까지 대기 → 확인 → 쏘기. 결과 dict 를 돌려준다."""
    target = state['ec2_start_ms'] + offset_ms
    observed = state['observed_ms']
    sleep_until(target)
    woke = now_ms()
    delivery_late = max(0, observed - target)
    if now_ms() - target > args.max_late_ms:
        execution_part = max(0, now_ms() - max(target, observed))
        cause = 'delivery' if delivery_late >= execution_part else 'execution'
        result = {'offset_ms': offset_ms, 'target_ms': target, 'status': 'skipped_late_' + cause,
                  'delivery_late_ms': delivery_late, 'execution_lag_ms': execution_part, 'woke_ms': woke}
        log.write(event='dump', **result)
        return result
    if not pid_unchanged(state['pid'], state['pid_ticks']):
        log.write(event='error', error='jvm_changed', offset_ms=offset_ms, at_ms=now_ms())
        raise SystemExit(2)
    fire = now_ms()
    exit_code, end = None, None
    if not args.dry_run:
        exit_code = subprocess.run([args.docker, 'kill', '--signal=SIGQUIT', args.app], capture_output=True,
                                   timeout=10).returncode
        end = now_ms()
    execution_lag = fire - max(target, observed)
    result = {'offset_ms': offset_ms, 'target_ms': target, 'woke_ms': woke, 'signal_start_ms': fire,
              'signal_end_ms': end, 'docker_exit': exit_code, 'dry_run': args.dry_run,
              'delivery_late_ms': delivery_late, 'execution_lag_ms': execution_lag, 'total_lag_ms': fire - target,
              'late_cause': classify(delivery_late, execution_lag, args.tolerance_ms),
              'status': 'dry_run' if args.dry_run else 'fired' if exit_code == 0 else 'docker_failed'}
    log.write(event='dump', **result)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--dir', required=True, help='events.jsonl · summary.json 을 새로 만들 폴더(이미 있어야 하고 파일은 덮어쓰지 않는다)')
    parser.add_argument('--app', required=True)
    parser.add_argument('--start-file', required=True)
    parser.add_argument('--offsets-ms', default='3000,8000')
    parser.add_argument('--tolerance-ms', type=int, default=500, help='이 값 이하의 지연은 정상(late_cause=none)')
    parser.add_argument('--max-late-ms', type=int, default=2000, help='목표보다 이만큼 넘게 늦으면 쏘지 않는다')
    parser.add_argument('--wait-seconds', type=float, default=900)
    parser.add_argument('--docker', default='docker')
    parser.add_argument('--dry-run', action='store_true', help='신호를 보내지 않고 같은 경로의 시각만 기록한다')
    args = parser.parse_args(argv)
    os.umask(0o077)
    base = Path(args.dir)
    log = Log(base / 'events.jsonl')
    offsets = [int(x) for x in args.offsets_ms.split(',')]
    pid = int(subprocess.run([args.docker, 'inspect', '--format', '{{.State.Pid}}', args.app], capture_output=True,
                             text=True, check=True, timeout=15).stdout.strip())
    if pid <= 0:
        log.write(event='error', error='app_not_running')
        return 2
    state = {'pid': pid, 'pid_ticks': proc_start_ticks(pid)}
    log.write(event='armed', armed_ms=now_ms(), pid=pid, offsets_ms=offsets, tolerance_ms=args.tolerance_ms,
              max_late_ms=args.max_late_ms, dry_run=args.dry_run, start_file=args.start_file)
    observed, start = wait_for_start(Path(args.start_file), args.wait_seconds)
    if start is None:
        log.write(event='no_start_signal', at_ms=now_ms())
        return 3
    state.update(observed_ms=observed, ec2_start_ms=start['ec2_start_ms'])
    log.write(event='start_observed', observed_ms=observed, ec2_start_ms=start['ec2_start_ms'], relay=start,
              delivery_ms=observed - start['ec2_start_ms'],
              detect_lag_ms=start['watcher_seen_mac_ms'] - start['scenario_start_mac_ms'],
              mac_prep_ms=start['watcher_sent_mac_ms'] - start['watcher_seen_mac_ms'],
              relay_transit_ms=observed - (start['watcher_sent_mac_ms'] + start['offset_ms']))
    results = [one_dump(args, log, state, offset) for offset in offsets]
    with (base / 'summary.json').open('x') as handle:
        json.dump({'dumps': results, 'pid': pid, 'ec2_start_ms': state['ec2_start_ms'], 'observed_ms': observed}, handle, indent=2)
    return 0 if all(r['status'] in ('fired', 'dry_run') for r in results) else 3


if __name__ == '__main__':
    sys.exit(main())
