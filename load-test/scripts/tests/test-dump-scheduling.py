#!/usr/bin/env python3
"""예약기(dump-scheduler.py)·맥 감시기(dump-start-relay.py)·k6 시작 시각 출력의 로컬 검증.

EC2·실제 docker/ssh·실제 서버 없음 — 가짜 docker/ssh 스크립트와 로컬 mock HTTP 서버만 쓴다.
음성 사례는 '의도한 늦음 종류로만' 판정되는지 단언한다. 마지막 클래스는 실제 k6(로컬, 40건)로 전체 연결을 돌린다.
실행: TMPDIR=<임시폴더> python3 -B load-test/scripts/tests/test-dump-scheduling.py
"""
import http.server
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import threading
import time
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
K6_SCRIPT = SCRIPTS.parents[0] / 'k6' / 'db-baseline-c.js'
SCHEDULER, RELAY = SCRIPTS / 'dump-scheduler.py', SCRIPTS / 'dump-start-relay.py'
spec = importlib.util.spec_from_file_location('dump_scheduler', SCHEDULER)
scheduler = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scheduler)

FAKE_DOCKER = """#!/bin/sh
case "$1" in
  inspect) echo "$FAKE_PID" ;;
  kill) echo "$(python3 -c 'import time;print(time.time_ns()//1000000)') $*" >> "$FAKE_LOG"; exit "${FAKE_KILL_RC:-0}" ;;
esac
"""
FAKE_SSH = """#!/bin/sh
for last; do :; done
case "$last" in *'cat >'*) [ -n "$FAKE_SSH_FAIL" ] && exit "$FAKE_SSH_FAIL" ;; esac
exec sh -c "$last"
"""


def now_ms():
    return time.time_ns() // 1000000


def executable(directory, name, body):
    path = Path(directory) / name
    path.write_text(body)
    path.chmod(path.stat().st_mode | stat.S_IXUSR)
    return str(path)


def write_start(path, ec2_start_ms):
    """맥 감시기가 쓰는 시작 파일과 같은 모양(오프셋 0, 약 5·10ms 뒤에 읽고 보냈다고 가정)."""
    tmp = str(path) + '.tmp'
    Path(tmp).write_text(json.dumps({'scenario_start_mac_ms': ec2_start_ms, 'offset_ms': 0, 'ec2_start_ms': ec2_start_ms,
                                     'watcher_seen_mac_ms': ec2_start_ms + 5, 'watcher_sent_mac_ms': ec2_start_ms + 10}))
    os.replace(tmp, path)


def read_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]


def wait_for(predicate, seconds=8):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if predicate():
            return True
        time.sleep(0.01)
    return False


class ClassifyTest(unittest.TestCase):
    def test_cause_table(self):
        tol = 500
        cases = {(0, 0): 'none', (500, 500): 'none', (501, 0): 'delivery', (0, 501): 'execution', (700, 900): 'both'}
        for (delivery, execution), expected in cases.items():
            self.assertEqual(scheduler.classify(delivery, execution, tol), expected, (delivery, execution))


class SchedulerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='dbb-sched-test-')
        self.root = Path(self.tmp.name)
        self.docker = executable(self.root, 'docker', FAKE_DOCKER)
        self.events_dir = self.root / 'out'
        self.events_dir.mkdir()
        self.start_file = self.root / 'start.json'
        self.kill_log = self.root / 'kills.log'
        self.kill_log.write_text('')
        self.procs = []

    def tearDown(self):
        for proc in self.procs:
            if proc.poll() is None:
                proc.kill()
        self.tmp.cleanup()

    def launch(self, *extra, pid=None, env_extra=None):
        env = dict(os.environ, FAKE_PID=str(pid or os.getpid()), FAKE_LOG=str(self.kill_log), **(env_extra or {}))
        proc = subprocess.Popen([sys.executable, '-B', str(SCHEDULER), '--dir', str(self.events_dir), '--app', 'fakeapp',
                                 '--start-file', str(self.start_file), '--docker', self.docker, *extra],
                                env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        self.procs.append(proc)
        events = self.events_dir / 'events.jsonl'
        self.assertTrue(wait_for(lambda: events.exists() and 'armed' in events.read_text()), '예약기가 대기 상태가 되지 않음')
        return proc

    def result(self, proc, timeout=15):
        code = proc.wait(timeout=timeout)
        events = read_jsonl(self.events_dir / 'events.jsonl')
        dumps = [e for e in events if e['event'] == 'dump']
        kills = [int(line.split()[0]) for line in self.kill_log.read_text().splitlines()]
        return code, events, dumps, kills

    def assert_never_early(self, dumps):
        for dump in dumps:
            if 'signal_start_ms' in dump:
                self.assertGreaterEqual(dump['signal_start_ms'], dump['target_ms'], '목표보다 일찍 쏨')

    def assert_identity(self, dump):
        self.assertEqual(dump['total_lag_ms'], dump['delivery_late_ms'] + dump['execution_lag_ms'])

    def test_on_time(self):
        proc = self.launch('--offsets-ms', '400,900', '--tolerance-ms', '250')
        write_start(self.start_file, now_ms())
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 0, events)
        self.assertEqual([d['status'] for d in dumps], ['fired', 'fired'])
        self.assertEqual([d['late_cause'] for d in dumps], ['none', 'none'], dumps)
        self.assertEqual(len(kills), 2)
        self.assert_never_early(dumps)
        for dump in dumps:
            self.assert_identity(dump)
            self.assertLess(dump['execution_lag_ms'], 250)
        observed = next(e for e in events if e['event'] == 'start_observed')
        self.assertEqual(observed['detect_lag_ms'], 5)
        self.assertEqual(observed['mac_prep_ms'], 5)

    def test_late_delivery_is_fired_and_labelled_delivery(self):
        proc = self.launch('--offsets-ms', '400,1000', '--tolerance-ms', '250', '--max-late-ms', '2000')
        write_start(self.start_file, now_ms() - 700)  # 첫 목표는 이미 약 300ms 지났다
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 0, events)
        first, second = dumps
        self.assertEqual((first['status'], first['late_cause']), ('fired', 'delivery'), first)
        self.assertGreater(first['delivery_late_ms'], 250)
        self.assertLess(first['execution_lag_ms'], 250)
        self.assertEqual((second['status'], second['late_cause']), ('fired', 'none'), second)
        self.assertEqual(len(kills), 2)
        self.assert_never_early(dumps)
        self.assert_identity(first)

    def test_too_late_delivery_is_skipped(self):
        proc = self.launch('--offsets-ms', '400,5600', '--tolerance-ms', '250', '--max-late-ms', '1000')
        write_start(self.start_file, now_ms() - 5000)
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 3)
        self.assertEqual(dumps[0]['status'], 'skipped_late_delivery', dumps[0])
        self.assertEqual(dumps[1]['status'], 'fired', dumps[1])
        self.assertEqual(len(kills), 1)

    def stall(self, proc, begin_ms, end_ms, start_ms):
        """예약기를 SIGSTOP 으로 멈췄다 풀어 '알고도 늦게 깨어남'을 만든다."""
        time.sleep(max(0, (start_ms + begin_ms - now_ms()) / 1000))
        os.kill(proc.pid, signal.SIGSTOP)
        time.sleep(max(0, (start_ms + end_ms - now_ms()) / 1000))
        os.kill(proc.pid, signal.SIGCONT)

    def test_late_execution_is_fired_and_labelled_execution(self):
        proc = self.launch('--offsets-ms', '700,1800', '--tolerance-ms', '200', '--max-late-ms', '2000')
        start = now_ms()
        write_start(self.start_file, start)
        self.stall(proc, 300, 1000, start)
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 0, events)
        first, second = dumps
        self.assertEqual((first['status'], first['late_cause']), ('fired', 'execution'), first)
        self.assertEqual(first['delivery_late_ms'], 0)
        self.assertGreater(first['execution_lag_ms'], 200)
        self.assertEqual(second['late_cause'], 'none', second)
        self.assert_never_early(dumps)
        self.assert_identity(first)

    def test_too_late_execution_is_skipped_and_labelled_execution(self):
        proc = self.launch('--offsets-ms', '500,3200', '--tolerance-ms', '200', '--max-late-ms', '600')
        start = now_ms()
        write_start(self.start_file, start)
        self.stall(proc, 200, 1700, start)
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 3)
        self.assertEqual(dumps[0]['status'], 'skipped_late_execution', dumps[0])
        self.assertEqual(dumps[0]['delivery_late_ms'], 0)
        self.assertEqual(dumps[1]['status'], 'fired', dumps[1])
        self.assertEqual(len(kills), 1)

    def test_dry_run_sends_nothing_but_measures(self):
        proc = self.launch('--offsets-ms', '300,600', '--dry-run')
        write_start(self.start_file, now_ms())
        code, events, dumps, kills = self.result(proc)
        self.assertEqual((code, kills), (0, []))
        self.assertEqual([d['status'] for d in dumps], ['dry_run', 'dry_run'])
        self.assertTrue(all(d['signal_end_ms'] is None and 'total_lag_ms' in d for d in dumps))

    def test_jvm_change_aborts_without_signal(self):
        gone = subprocess.Popen(['sleep', '0.2'])
        pid = gone.pid
        proc = self.launch('--offsets-ms', '900', pid=pid)
        gone.wait()  # 수거해야 좀비로 남지 않는다
        write_start(self.start_file, now_ms())
        code, events, dumps, kills = self.result(proc)
        self.assertEqual(code, 2)
        self.assertEqual(kills, [])
        self.assertIn('jvm_changed', [e.get('error') for e in events])

    def test_no_start_signal(self):
        proc = self.launch('--wait-seconds', '0.5')
        code, events, dumps, kills = self.result(proc)
        self.assertEqual((code, kills), (3, []))
        self.assertIn('no_start_signal', [e['event'] for e in events])

    def test_docker_failure_is_reported(self):
        proc = self.launch('--offsets-ms', '300', env_extra={'FAKE_KILL_RC': '1'})
        write_start(self.start_file, now_ms())
        code, events, dumps, kills = self.result(proc)
        self.assertEqual((code, dumps[0]['status']), (3, 'docker_failed'))


class RelayTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='dbb-relay-test-')
        self.root = Path(self.tmp.name)
        self.ssh = executable(self.root, 'ssh', FAKE_SSH)
        self.console = self.root / 'k6-c-console.log'
        self.remote = self.root / 'start.json'
        self.relay_log = self.root / 'dump-relay.jsonl'

    def tearDown(self):
        self.tmp.cleanup()

    def run_relay(self, *extra, env_extra=None, writer=None, timeout=15):
        env = dict(os.environ, **(env_extra or {}))
        proc = subprocess.Popen([sys.executable, '-B', str(RELAY), '--console-log', str(self.console), '--offset-ms', '16',
                                 '--remote-file', str(self.remote), '--ssh-cmd', self.ssh, '--log', str(self.relay_log),
                                 '--poll-ms', '2', *extra], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            if writer:
                assert wait_for(lambda: self.relay_log.exists() and 'waiting' in self.relay_log.read_text())
                writer()
            code = proc.wait(timeout=timeout)
        finally:
            if proc.poll() is None:
                proc.kill()
        return code, read_jsonl(self.relay_log)

    def append(self, text):
        with self.console.open('a') as handle:
            handle.write(text)
            handle.flush()

    def test_delivers_marker_with_offset_and_timestamps(self):
        marker = {}

        def writer():
            self.append('time="x" level=info msg="Init"\n')
            time.sleep(0.15)
            marker['ms'] = now_ms()
            self.append('time="x" level=info msg="SCENARIO_START_MS=%d" source=console\n' % marker['ms'])

        code, events = self.run_relay(writer=writer)
        self.assertEqual(code, 0, events)
        sent = json.loads(self.remote.read_text())
        self.assertEqual((sent['scenario_start_mac_ms'], sent['ec2_start_ms']), (marker['ms'], marker['ms'] + 16))
        self.assertGreaterEqual(sent['watcher_seen_mac_ms'], sent['scenario_start_mac_ms'])
        self.assertLessEqual(sent['watcher_seen_mac_ms'] - sent['scenario_start_mac_ms'], 150)
        self.assertGreaterEqual(sent['watcher_sent_mac_ms'], sent['watcher_sent_mac_ms'] and sent['watcher_seen_mac_ms'])
        self.assertEqual(oct(self.remote.stat().st_mode & 0o777), '0o600')
        self.assertFalse(Path(str(self.remote) + '.tmp').exists())

    def test_fractional_value_and_first_marker_only(self):
        def writer():
            self.append('msg="SCENARIO_START_MS=1793157300123.456"\nmsg="SCENARIO_START_MS=1793157399999"\n')

        code, _ = self.run_relay(writer=writer)
        self.assertEqual((code, json.loads(self.remote.read_text())['scenario_start_mac_ms']), (0, 1793157300123))

    def test_no_marker_times_out(self):
        self.console.write_text('only unrelated lines\n')
        code, events = self.run_relay('--wait-seconds', '0.6')
        self.assertEqual((code, self.remote.exists()), (3, False))
        self.assertIn('no_marker', [e['event'] for e in events])

    def test_ssh_failure_retries_once_and_reports_failure(self):
        code, events = self.run_relay(env_extra={'FAKE_SSH_FAIL': '255'}, writer=lambda: self.append('SCENARIO_START_MS=1793157300123\n'))
        self.assertEqual((code, self.remote.exists()), (4, False))
        self.assertEqual([e['attempt'] for e in events if e['event'] == 'ssh'], [1, 2])

    def test_existing_remote_file_is_not_overwritten_or_retried(self):
        self.remote.write_text('OLD')
        code, events = self.run_relay(writer=lambda: self.append('SCENARIO_START_MS=1793157300123\n'))
        self.assertEqual((code, self.remote.read_text()), (4, 'OLD'))
        self.assertEqual(len([e for e in events if e['event'] == 'ssh']), 1)


class K6PipelineTest(unittest.TestCase):
    """실제 k6(로컬 mock 서버, 40건) → 콘솔 로그 → 감시기 → 예약기. 시작 시각이 요약 값과 같은지와 지연을 잰다."""

    @classmethod
    def setUpClass(cls):
        class Handler(http.server.BaseHTTPRequestHandler):
            count = 0

            def do_POST(self):
                Handler.count += 1
                if Handler.count <= 9:
                    self.send_response(201)
                    body = b'{"success":true}'
                else:
                    self.send_response(409)
                    body = b'{"success":false,"data":null,"error":{"code":"CR002"}}'
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    @unittest.skipUnless(shutil.which('k6'), 'k6 없음')
    def test_chain(self):
        with tempfile.TemporaryDirectory(prefix='dbb-chain-test-') as tmp:
            root = Path(tmp)
            out = root / 'out'
            out.mkdir()
            tokens = root / 'tokens.csv'
            tokens.write_text('token\n' + ''.join('fake-token-%d\n' % i for i in range(1, 41)))
            tokens.chmod(0o600)
            docker, ssh = executable(root, 'docker', FAKE_DOCKER), executable(root, 'ssh', FAKE_SSH)
            kill_log, start_file = root / 'kills.log', root / 'start.json'
            kill_log.write_text('')
            sched_dir = root / 'sched'
            sched_dir.mkdir()
            console, relay_log = out / 'k6-c-console.log', out / 'dump-relay.jsonl'
            env = dict(os.environ, FAKE_PID=str(os.getpid()), FAKE_LOG=str(kill_log))
            sched = subprocess.Popen([sys.executable, '-B', str(SCHEDULER), '--dir', str(sched_dir), '--app', 'fakeapp',
                                      '--start-file', str(start_file), '--docker', docker, '--offsets-ms', '300,900',
                                      '--tolerance-ms', '400', '--wait-seconds', '30'], env=env, stderr=subprocess.PIPE)
            relay = subprocess.Popen([sys.executable, '-B', str(RELAY), '--console-log', str(console), '--offset-ms', '0',
                                      '--remote-file', str(start_file), '--ssh-cmd', ssh, '--log', str(relay_log),
                                      '--wait-seconds', '30', '--poll-ms', '2'], env=env, stderr=subprocess.PIPE)
            try:
                self.assertTrue(wait_for(lambda: relay_log.exists() and 'waiting' in relay_log.read_text()))
                self.assertTrue(wait_for(lambda: (sched_dir / 'events.jsonl').exists()))
                cmd = ['k6', 'run', '--out', 'json=%s' % (out / 'k6-c.json')]
                for key, value in {'BASE_URL': 'http://127.0.0.1:%d' % self.server.server_address[1], 'CREW_ID': 'dbb-e2e',
                                   'RUN_ID': 'e2e-test', 'TARGET_RPS': '20', 'TOTAL': '40', 'PRE_VUS': '60', 'MAX_VUS': '60',
                                   'WARMUP': '1', 'REQ_TIMEOUT_SECONDS': '5', 'TOKENS_FILE': str(tokens), 'OUT_DIR': str(out)}.items():
                    cmd += ['-e', '%s=%s' % (key, value)]
                cmd.append(str(K6_SCRIPT))
                # run-db-c.py 와 같은 방식: k6 출력을 한 줄씩 파일에 쓰고 flush 한다.
                with console.open('x') as log:
                    k6 = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                    for raw in iter(k6.stdout.readline, b''):
                        log.write(raw.decode('utf-8', errors='replace'))
                        log.flush()
                    k6.wait()
                self.assertEqual(relay.wait(timeout=20), 0, relay.stderr.read())
                self.assertEqual(sched.wait(timeout=20), 0, sched.stderr.read())
            finally:
                for proc in (sched, relay):
                    if proc.poll() is None:
                        proc.kill()
            lines = [line for line in console.read_text().splitlines() if 'SCENARIO_START_MS=' in line]
            self.assertEqual(len(lines), 1, lines)
            summary = json.loads((out / 'warmup-c-summary.json').read_text())['dbc']
            from_console = int(lines[0].split('SCENARIO_START_MS=')[1].split('"')[0].split('.')[0])
            self.assertEqual(from_console, int(summary['scenario_start_epoch_ms']), '콘솔 값 ≠ 요약의 scenario_start_epoch_ms')
            self.assertEqual(json.loads(start_file.read_text())['scenario_start_mac_ms'], from_console)
            events = read_jsonl(sched_dir / 'events.jsonl')
            observed = next(e for e in events if e['event'] == 'start_observed')
            dumps = [e for e in events if e['event'] == 'dump']
            self.assertEqual([d['status'] for d in dumps], ['fired', 'fired'], dumps)
            for dump in dumps:
                self.assertGreaterEqual(dump['signal_start_ms'], dump['target_ms'])
            print('\n[chain] detect_lag_ms=%d mac_prep_ms=%d relay_transit_ms=%d delivery_ms=%d | dumps: %s' % (
                observed['detect_lag_ms'], observed['mac_prep_ms'], observed['relay_transit_ms'], observed['delivery_ms'],
                [(d['offset_ms'], d['late_cause'], d['delivery_late_ms'], d['execution_lag_ms']) for d in dumps]))
            print('[chain] console line: %s' % lines[0][:110])


if __name__ == '__main__':
    unittest.main(verbosity=2)
