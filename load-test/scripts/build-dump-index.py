#!/usr/bin/env python3
"""회수한 앱 로그(docker logs --timestamps)에서 스레드 덤프(SIGQUIT 출력)를 잘라 파일과 index.json 을 새로 만든다.

원본 로그는 읽기만 한다. 덤프는 `Full thread dump` 줄부터 `JNI global refs:` 줄까지(JNI 줄이 없으면 불완전 표시)이며,
파일에는 docker 타임스탬프 접두를 뗀 원문을 쓰고 원본 줄 번호(1-based)와 첫 줄 시각을 index 에 남긴다.
예약기 기록(events.jsonl)을 주면 신호 시각 창 안에 첫 줄이 있는 덤프를 그 신호와 짝지어 목표·지연 정보를 붙인다.
덤프는 그 순간의 스냅샷이다 — 전체 요청 이력의 증거로 확대하지 않는다.
사용: python3 -I build-dump-index.py --app-log <app.log> --out-dir <새 폴더> [--events events.jsonl] [--offset-ms 16]
      --offset-ms 는 EC2−맥(clock-check.sh) — 주면 맥 보정 KST 시각을 함께 적는다.
종료 코드: 0 · 3 덤프 없음 · 1 입력 오류
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sys

STAMP = re.compile(r'^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)\.(\d{1,9})Z (.*)$')
WORKER = re.compile(r'^"http-nio-\d+-exec-\d+"')
STATE = re.compile(r'java\.lang\.Thread\.State: (\w+)')
KST = datetime.timezone(datetime.timedelta(hours=9))


def parse_line(raw):
    """(epoch_ms, 접두 제거한 원문) — 타임스탬프 접두가 없으면 (None, 원문)."""
    match = STAMP.match(raw)
    if not match:
        return None, raw
    base = datetime.datetime.strptime(match.group(1), '%Y-%m-%dT%H:%M:%S').replace(tzinfo=datetime.timezone.utc)
    return int(base.timestamp()) * 1000 + int(match.group(2).ljust(9, '0')[:3]), match.group(3)


def stamp_text(raw):
    return raw.split(' ', 1)[0] if STAMP.match(raw) else None


def find_dumps(lines):
    """[(begin, end, complete)] — 1-based 줄 번호. 다음 덤프가 시작되면 앞 덤프는 불완전로 닫는다."""
    dumps, begin = [], None
    for number, raw in enumerate(lines, 1):
        text = parse_line(raw)[1]
        if text.startswith('Full thread dump '):
            if begin is not None:
                dumps.append((begin, number - 1, False))
            begin = number
        elif begin is not None and text.startswith('JNI global refs:'):
            dumps.append((begin, number, True))
            begin = None
    if begin is not None:
        dumps.append((begin, len(lines), False))
    return dumps


def tomcat_summary(texts):
    """Tomcat 작업 스레드 수·상태 분포·Hikari 연결 대기(TIMED_WAITING 이면서 ConcurrentBag.borrow 에 있음)."""
    states, waiters, workers, block = {}, 0, 0, None

    def close(current):
        nonlocal waiters
        if current and current['worker']:
            states[current['state']] = states.get(current['state'], 0) + 1
            if current['state'] == 'TIMED_WAITING' and current['borrow']:
                waiters += 1

    for text in texts:
        if text.startswith('"'):
            close(block)
            is_worker = bool(WORKER.match(text))
            workers += is_worker
            block = {'worker': is_worker, 'state': 'UNKNOWN', 'borrow': False}
        elif block is not None:
            found = STATE.search(text)
            if found:
                block['state'] = found.group(1)
            block['borrow'] = block['borrow'] or 'ConcurrentBag.borrow' in text
    close(block)
    return workers, states, waiters


def iso_ms(epoch_ms, tz):
    return datetime.datetime.fromtimestamp(epoch_ms / 1000, tz).isoformat(timespec='milliseconds')


def load_events(path):
    if not path:
        return None, []
    events = [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]
    start = next((e for e in events if e['event'] == 'start_observed'), None)
    return start, [e for e in events if e['event'] == 'dump' and e.get('status') == 'fired']


def match_event(first_ms, fired, window_ms, used):
    for index, event in enumerate(fired):
        if index in used or first_ms is None:
            continue
        if event['signal_start_ms'] - 50 <= first_ms <= event['signal_end_ms'] + window_ms:
            used.add(index)
            return event
    return None


def build(args):
    lines = Path(args.app_log).read_text(encoding='utf-8', errors='replace').split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    start, fired = load_events(args.events)
    out = Path(args.out_dir)
    out.mkdir(mode=0o700, exist_ok=False)
    used, entries, counter = set(), [], {'main': 0, 'preflight': 0}
    for begin, end, complete in find_dumps(lines):
        texts = [parse_line(line)[1] for line in lines[begin - 1:end]]
        first_ms = parse_line(lines[begin - 1])[0]
        event = match_event(first_ms, fired, args.match_window_ms, used)
        phase = 'main' if event else 'preflight'
        counter[phase] += 1
        name = 'thread-dump-%s-%d.txt' % (phase, counter[phase])
        with (out / name).open('x') as handle:
            handle.write('\n'.join(texts) + '\n')
        workers, states, waiters = tomcat_summary(texts)
        entry = {'file': name, 'phase': phase, 'source_line_begin': begin, 'source_line_end': end,
                 'recorded_time_ec2': stamp_text(lines[begin - 1]), 'recorded_epoch_ms_ec2': first_ms,
                 'complete_JNI_end': complete, 'tomcat_workers': workers, 'tomcat_states': states,
                 'hikari_connection_waiters_TIMED_WAITING_park_borrow': waiters}
        if args.offset_ms is not None and first_ms is not None:
            entry['time_kst_mac_adjusted'] = iso_ms(first_ms - args.offset_ms, KST)
        if start and first_ms is not None:
            entry['ms_after_scenario_start'] = first_ms - start['ec2_start_ms']
        if event:
            entry['scheduled'] = {k: event.get(k) for k in ('offset_ms', 'target_ms', 'signal_start_ms', 'signal_end_ms', 'late_cause',
                                                             'delivery_late_ms', 'execution_lag_ms', 'total_lag_ms')}
        entries.append(entry)
    missing = [e for i, e in enumerate(fired) if i not in used]
    index = {'source_app_log': str(args.app_log), 'offset_ms_ec2_minus_mac': args.offset_ms, 'dumps': entries,
             'fired_without_dump': [{k: e.get(k) for k in ('offset_ms', 'target_ms', 'signal_start_ms')} for e in missing]}
    with (out / 'index.json').open('x') as handle:
        json.dump(index, handle, indent=2, ensure_ascii=False)
        handle.write('\n')
    return entries, missing


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--app-log', required=True)
    parser.add_argument('--out-dir', required=True, help='새로 만든다(이미 있으면 거부)')
    parser.add_argument('--events')
    parser.add_argument('--offset-ms', type=int)
    parser.add_argument('--match-window-ms', type=int, default=3000, help='신호 종료 뒤 이만큼까지 첫 줄이 오면 같은 덤프로 본다')
    args = parser.parse_args(argv)
    if not Path(args.app_log).is_file() or Path(args.out_dir).exists():
        print('FAIL input: app-log 없음 또는 out-dir 이 이미 있다')
        return 1
    entries, missing = build(args)
    for entry in entries:
        print('%-26s lines %d-%d complete=%s workers=%d hikari_waiters=%d states=%s' % (
            entry['file'], entry['source_line_begin'], entry['source_line_end'], entry['complete_JNI_end'],
            entry['tomcat_workers'], entry['hikari_connection_waiters_TIMED_WAITING_park_borrow'], entry['tomcat_states']))
    for event in missing:
        print('WARN 신호는 나갔으나 짝지어진 덤프 없음: offset=%s' % event.get('offset_ms'))
    return 0 if entries else 3


if __name__ == '__main__':
    sys.exit(main())
