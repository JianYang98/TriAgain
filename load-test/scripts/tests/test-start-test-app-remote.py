#!/usr/bin/env python3
"""start-test-app-remote.py 의 선택 기능(JVM 옵션 검증·비공개 env 읽기)을 로컬에서 확인한다. 원격·docker 없음.

사용법: python3 -I test-start-test-app-remote.py
"""
import importlib.util
import os
import pathlib
import sys
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parent


def load_tool():
    spec = importlib.util.spec_from_file_location('start_tool', HERE.parent / 'start-test-app-remote.py')
    module = importlib.util.module_from_spec(spec)
    saved = sys.argv
    sys.argv = ['start-test-app-remote.py', '{}']  # 모듈 로드 시 JSON 설정을 읽는다 — 빈 설정
    try:
        spec.loader.exec_module(module)
    finally:
        sys.argv = saved
    return module


T = load_tool()
GC_LOG = '-Xlog:gc=info,gc+cpu=info:file=/access-logs/gc-%t.log:time,uptime:filesize=0'
DUMMY = 'dummy-not-a-real-value-123'


class JvmOptions(unittest.TestCase):
    def test_approved_gc_log_option_is_accepted(self):
        self.assertEqual(T.validate_jvm_options([GC_LOG]), [GC_LOG])

    def test_empty_is_accepted(self):
        self.assertEqual(T.validate_jvm_options([]), [])

    def test_heap_and_gc_algorithm_options_are_rejected(self):
        for bad in ('-Xmx512m', '-Xms256m', '-Xss1m', '-XX:+UseG1GC', '-XX:-UseSerialGC', '-XX:MaxRAMPercentage=50',
                    '-XX:InitialRAMPercentage=25', '-XX:MaxHeapSize=1g', '-XX:NewSize=64m'):
            with self.assertRaises(RuntimeError, msg=bad):
                T.validate_jvm_options([GC_LOG, bad])

    def test_non_x_options_and_wrong_types_are_rejected(self):
        for bad in (['--add-opens=java.base/java.lang=ALL-UNNAMED'], ['-Dfoo=bar'], [1], 'not-a-list'):
            with self.assertRaises(RuntimeError, msg=str(bad)):
                T.validate_jvm_options(bad)


class ExtraEnv(unittest.TestCase):
    def write(self, text, mode=0o600):
        d = tempfile.mkdtemp(prefix='extra-env-test-')
        path = os.path.join(d, 'app-extra.env')
        with open(path, 'w') as f:
            f.write(text)
        os.chmod(path, mode)
        return path

    def test_private_file_is_read_and_names_listed(self):
        path = self.write('APPLE_REFRESH_KEY=%s\n' % DUMMY)
        lines = T.read_extra_env(path, ['DB_URL', 'INTERNAL_API_KEY'])
        self.assertEqual(lines, ['APPLE_REFRESH_KEY=%s' % DUMMY])
        self.assertEqual(T.env_names(lines), ['APPLE_REFRESH_KEY'])

    def test_group_or_world_readable_file_is_rejected(self):
        for mode in (0o640, 0o644, 0o604, 0o660):
            path = self.write('APPLE_REFRESH_KEY=%s\n' % DUMMY, mode)
            with self.assertRaises(RuntimeError, msg=oct(mode)):
                T.read_extra_env(path, [])

    def test_bad_lines_are_rejected_without_leaking_the_value(self):
        for text in ('apple_refresh_key=%s\n' % DUMMY, 'APPLE_REFRESH_KEY=\n', 'no equals sign %s\n' % DUMMY):
            path = self.write(text)
            with self.assertRaises(RuntimeError) as ctx:
                T.read_extra_env(path, [])
            self.assertNotIn(DUMMY, str(ctx.exception))

    def test_duplicate_with_template_env_is_rejected_without_leaking(self):
        path = self.write('INTERNAL_API_KEY=%s\n' % DUMMY)
        with self.assertRaises(RuntimeError) as ctx:
            T.read_extra_env(path, ['INTERNAL_API_KEY'])
        self.assertNotIn(DUMMY, str(ctx.exception))

    def test_duplicate_inside_file_is_rejected(self):
        path = self.write('A_KEY=1\nA_KEY=2\n')
        with self.assertRaises(RuntimeError):
            T.read_extra_env(path, [])

    def test_oversized_file_is_rejected(self):
        path = self.write('A_KEY=%s\n' % ('x' * 5000))
        with self.assertRaises(RuntimeError):
            T.read_extra_env(path, [])


class AcceptCount(unittest.TestCase):
    ARGS = ['-jar', 'app.jar', '--server.tomcat.accept-count=100',
            '--server.tomcat.threads.max=200', '--triagain.crew.lock-strategy=CONDITIONAL']
    REDIS = {'host': 'synthetic-host', 'port': 6379, 'namespace': 'test', 'run_id': 'test-run'}

    def legacy(self, args, redis):
        if not redis:
            return args
        return [a for a in args if not a.startswith('--triagain.crew.lock-strategy=')] + [
            '--triagain.crew.lock-strategy=REDIS_ASYNC', '--spring.data.redis.host=synthetic-host',
            '--spring.data.redis.port=6379', '--triagain.crew.redis.namespace=test',
            '--triagain.crew.redis.run-id=test-run']

    def test_absent_key_preserves_legacy_args_with_and_without_redis(self):
        for redis in (None, self.REDIS):
            for args in (self.ARGS, ['--other=value']):
                self.assertEqual(T.swap_args(args, redis), self.legacy(args, redis))

    def test_replaces_in_place_with_and_without_redis(self):
        for redis in (None, self.REDIS):
            original = list(self.ARGS)
            expected = list(self.legacy(original, redis))
            expected[2] = '--server.tomcat.accept-count=4096'
            self.assertEqual(T.swap_args(original, redis, 4096), expected)
            self.assertEqual(original, self.ARGS)

    def test_missing_and_duplicate_template_options_are_rejected(self):
        for redis in (None, self.REDIS):
            for args in (self.ARGS[:2], self.ARGS + ['--server.tomcat.accept-count=256']):
                with self.assertRaisesRegex(RuntimeError, 'accept_count_exactly_one'):
                    T.swap_args(args, redis, 4096)

    def test_invalid_values_are_rejected(self):
        for value in (0, 65536, -1, True, False, '4096', None, 4096.0):
            with self.subTest(value=value):
                with self.assertRaisesRegex(RuntimeError, 'accept_count_range'):
                    T.swap_args(self.ARGS, None, value)

    def test_boundary_values_are_accepted(self):
        for value in (1, 65535):
            self.assertEqual(T.swap_args(self.ARGS, None, value)[2],
                             '--server.tomcat.accept-count=' + str(value))


if __name__ == '__main__':
    unittest.main(verbosity=2)
