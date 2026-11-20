#!/usr/bin/env python3
"""build-dump-index.py 로컬 검증 — 합성 로그 + (있으면) 지난 run 의 실제 로그를 정답으로 대조한다.
실행: TMPDIR=<임시폴더> python3 -B load-test/scripts/tests/test-build-dump-index.py
"""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_dump_index', os.environ.get('DBB_BUILDER') or SCRIPTS / 'build-dump-index.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
PREV = SCRIPTS.parents[0] / 'results/raw/20261024/20261024-071326-db-200-fc18/jvm-diagnostics'
EC2_START = 1793157800000  # 2026-10-28T03:43:20Z


def stamp(offset_ms):
    """EC2_START + offset_ms 의 docker 타임스탬프 접두(나노초 9자리)."""
    epoch = EC2_START + offset_ms
    import datetime
    base = datetime.datetime.fromtimestamp(epoch // 1000, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S')
    return '%s.%03d000000Z' % (base, epoch % 1000)


DUMP = [
    'Full thread dump OpenJDK 64-Bit Server VM (17 mixed mode):', '',
    '"http-nio-8080-exec-1" #41 daemon prio=5', '   java.lang.Thread.State: TIMED_WAITING (parking)',
    '\tat com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:100)', '',
    '"http-nio-8080-exec-2" #42 daemon prio=5', '   java.lang.Thread.State: TIMED_WAITING (parking)',
    '\tat com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:100)', '',
    '"http-nio-8080-exec-3" #43 daemon prio=5', '   java.lang.Thread.State: RUNNABLE',
    '\tat com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:100)', '',   # RUNNABLE → 대기 아님
    '"http-nio-8080-exec-4" #44 daemon prio=5', '   java.lang.Thread.State: WAITING (parking)',
    '\tat com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:100)', '',   # WAITING(무기한) → 대기 아님
    '"HikariPool-1 housekeeper" #50 daemon', '   java.lang.Thread.State: TIMED_WAITING (parking)',
    '\tat com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:100)', '',   # Tomcat 작업 스레드가 아님
    '"main" #1', '   java.lang.Thread.State: WAITING', '',
    'JNI global refs: 12, weak refs: 3',
]


def render(parts):
    """parts: [(offset_ms, [원문 줄…])] → docker 로그 줄 목록(줄마다 접두)."""
    return [stamp(ms) + ' ' + text for ms, texts in parts for text in texts]


def event(offset, target, start, end, **extra):
    return {'event': 'dump', 'status': 'fired', 'offset_ms': offset, 'target_ms': EC2_START + target,
            'signal_start_ms': EC2_START + start, 'signal_end_ms': EC2_START + end, 'late_cause': 'none',
            'delivery_late_ms': 0, 'execution_lag_ms': start - target, 'total_lag_ms': start - target, **extra}


class IndexBuilderTest(unittest.TestCase):
    def run_builder(self, log_lines, events=None, offset=None, extra_args=()):
        with tempfile.TemporaryDirectory(prefix='dbb-index-test-') as tmp:
            root = Path(tmp)
            (root / 'app.log').write_text('\n'.join(log_lines) + '\n')
            args = ['--app-log', str(root / 'app.log'), '--out-dir', str(root / 'out'), *extra_args]
            if events is not None:
                (root / 'events.jsonl').write_text('\n'.join(json.dumps(e) for e in events) + '\n')
                args += ['--events', str(root / 'events.jsonl')]
            if offset is not None:
                args += ['--offset-ms', str(offset)]
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                code = builder.main(args)
            index = json.loads((root / 'out' / 'index.json').read_text()) if (root / 'out' / 'index.json').exists() else None
            dumps = {p.name: p.read_text() for p in (root / 'out').glob('thread-dump-*.txt')} if (root / 'out').exists() else {}
            return code, index, dumps, buffer.getvalue()

    def test_counts_only_tomcat_timed_waiting_borrow(self):
        code, index, dumps, _ = self.run_builder(render([(0, ['noise']), (3100, DUMP)]))
        entry = index['dumps'][0]
        self.assertEqual(code, 0)
        self.assertEqual(entry['tomcat_workers'], 4)
        self.assertEqual(entry['hikari_connection_waiters_TIMED_WAITING_park_borrow'], 2)
        self.assertEqual(entry['tomcat_states'], {'TIMED_WAITING': 2, 'RUNNABLE': 1, 'WAITING': 1})
        self.assertTrue(entry['complete_JNI_end'])
        self.assertNotIn('Z ', dumps['thread-dump-preflight-1.txt'].splitlines()[0])  # 접두 제거

    def test_matches_fired_events_and_marks_the_rest_preflight(self):
        log = render([(100, ['noise']), (500, DUMP), (3010, DUMP), (3500, ['noise']), (8120, DUMP)])
        events = [{'event': 'start_observed', 'ec2_start_ms': EC2_START, 'observed_ms': EC2_START + 30},
                  event(3000, 3000, 3002, 3009, late_cause='none'), event(8000, 8000, 8100, 8110, late_cause='execution')]
        code, index, _, _ = self.run_builder(log, events, offset=16)
        phases = [d['phase'] for d in index['dumps']]
        self.assertEqual(phases, ['preflight', 'main', 'main'])
        second, third = index['dumps'][1], index['dumps'][2]
        self.assertEqual((second['scheduled']['offset_ms'], third['scheduled']['offset_ms']), (3000, 8000))
        self.assertEqual(third['scheduled']['late_cause'], 'execution')
        self.assertEqual((second['ms_after_scenario_start'], third['ms_after_scenario_start']), (3010, 8120))
        self.assertIn('time_kst_mac_adjusted', second)
        self.assertEqual(index['fired_without_dump'], [])

    def test_fired_signal_without_dump_is_reported(self):
        log = render([(3010, DUMP)])
        events = [event(3000, 3000, 3002, 3009), event(8000, 8000, 8100, 8110)]
        code, index, _, out = self.run_builder(log, events)
        self.assertEqual([d['phase'] for d in index['dumps']], ['main'])
        self.assertEqual([e['offset_ms'] for e in index['fired_without_dump']], [8000])
        self.assertIn('짝지어진 덤프 없음', out)

    def test_incomplete_dump_is_marked_and_closed_at_next_dump(self):
        truncated = DUMP[:-1]  # JNI 줄 없음
        log = render([(1000, truncated), (2000, DUMP)])
        _, index, _, _ = self.run_builder(log)
        first, second = index['dumps']
        self.assertEqual((first['complete_JNI_end'], second['complete_JNI_end']), (False, True))
        self.assertEqual(first['source_line_end'], second['source_line_begin'] - 1)

    def test_incomplete_dump_at_end_of_log(self):
        code, index, _, _ = self.run_builder(render([(1000, DUMP[:-1])]))
        self.assertEqual((code, index['dumps'][0]['complete_JNI_end']), (0, False))

    def test_no_dump_exit_3(self):
        code, index, _, _ = self.run_builder(render([(0, ['just', 'noise'])]))
        self.assertEqual((code, index['dumps']), (3, []))

    def test_existing_out_dir_is_refused(self):
        with tempfile.TemporaryDirectory(prefix='dbb-index-test-') as tmp:
            (Path(tmp) / 'app.log').write_text(stamp(0) + ' x\n')
            (Path(tmp) / 'out').mkdir()
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(builder.main(['--app-log', str(Path(tmp) / 'app.log'), '--out-dir', str(Path(tmp) / 'out')]), 1)

    @unittest.skipUnless((PREV / 'app-final.log').is_file(), '지난 run 로그 없음')
    def test_matches_previous_run_ground_truth(self):
        old = json.loads((PREV / 'thread-dumps/index.json').read_text())
        with tempfile.TemporaryDirectory(prefix='dbb-index-test-') as tmp:
            with contextlib.redirect_stdout(io.StringIO()):
                builder.main(['--app-log', str(PREV / 'app-final.log'), '--out-dir', str(Path(tmp) / 'out'), '--offset-ms', '2033'])
            new = json.loads((Path(tmp) / 'out/index.json').read_text())['dumps']
        keys = ['source_line_begin', 'source_line_end', 'recorded_time_ec2', 'time_kst_mac_adjusted', 'complete_JNI_end',
                'tomcat_workers', 'hikari_connection_waiters_TIMED_WAITING_park_borrow']
        self.assertEqual([{k: d[k] for k in keys} for d in new], [{k: d[k] for k in keys} for d in old])


if __name__ == '__main__':
    unittest.main(verbosity=2)
