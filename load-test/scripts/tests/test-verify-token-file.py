#!/usr/bin/env python3
"""verify-token-file.py 로컬 검증 — 가짜 JWT와 로컬 mock 서버만 쓴다. EC2·실제 토큰·외부 통신 없음.

각 음성 사례는 '다른 이유가 아니라 의도한 검사만' 실패하는지 확인한다(실패한 검사 이름의 집합을 단언).
실행: python3 -B load-test/scripts/tests/test-verify-token-file.py
"""
import base64
import contextlib
import http.server
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'verify-token-file.py'
spec = importlib.util.spec_from_file_location('verify_token_file', SCRIPT)
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)
N = 20


def b64(data):
    return base64.urlsafe_b64encode(json.dumps(data).encode()).decode().rstrip('=')


def make_token(number, iat, ttl):
    return '%s.%s.sig%d' % (b64({'alg': 'none'}), b64({'sub': 'loadtest-user-%d' % number, 'iat': iat, 'exp': iat + ttl}), number)


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        token = self.headers.get('Authorization', '').replace('Bearer ', '')
        mode = self.server.mode
        status, body = 200, {'data': {'id': verifier.jwt_payload(token)['sub']}}
        if mode == 'unauth':
            status, body = 401, {}
        elif mode == 'wrong_user':
            body = {'data': {'id': 'someone-else'}}
        self.send_response(status)
        self.end_headers()
        self.wfile.write(json.dumps(body).encode())

    def log_message(self, *args):
        pass


class VerifyTokenFileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.server.mode = 'ok'
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.url = 'http://127.0.0.1:%d' % cls.server.server_address[1]

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def build(self, tmp, rows=None, file_mode=0o600, dir_mode=0o700, ttl=86400, iat_age=0, header='token'):
        folder = Path(tmp) / 'tokens-test'
        folder.mkdir()
        iat = int(time.time()) - iat_age
        rows = rows if rows is not None else [make_token(i, iat, ttl) for i in range(1, N + 1)]
        (folder / 'tokens.csv').write_text(header + '\n' + '\n'.join(rows) + '\n')
        os.chmod(folder / 'tokens.csv', file_mode)
        os.chmod(folder, dir_mode)
        return folder, rows

    def run_verifier(self, folder, mode='ok'):
        self.server.mode = mode
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = verifier.main([str(folder), '--base-url', self.url, '--expected', str(N), '--samples', '1,10'])
        status = json.loads((folder / 'issuance-status.json').read_text()) if (folder / 'issuance-status.json').exists() else {}
        failing = {name for name, passed in status.get('checks', {}).items() if not passed}
        return code, failing, out.getvalue(), status

    def expect_failing(self, expected, **kwargs):
        mode = kwargs.pop('server_mode', 'ok')
        with tempfile.TemporaryDirectory(prefix='dbb-token-verify-') as tmp:
            folder, rows = self.build(tmp, **kwargs)
            code, failing, out, _ = self.run_verifier(folder, mode)
            self.assertEqual(failing, expected, out)
            self.assertEqual(code, 2)
            self.assertFalse(any(token in out for token in rows if token), '토큰 값이 출력됨')

    def test_valid_passes_and_prints_no_token(self):
        with tempfile.TemporaryDirectory(prefix='dbb-token-verify-') as tmp:
            folder, rows = self.build(tmp)
            code, failing, out, status = self.run_verifier(folder)
            self.assertEqual((code, failing, status['status']), (0, set(), 'validated'), out)
            self.assertFalse(any(token in out for token in rows), '토큰 값이 출력됨')
            self.assertEqual(oct((folder / 'issuance-status.json').stat().st_mode & 0o777), '0o600')
            self.assertFalse(status['token_values_printed'])
            self.assertEqual((status['duplicate_count'], status['subject_sequence_valid']), (0, True))

    def test_refuses_to_overwrite_status(self):
        with tempfile.TemporaryDirectory(prefix='dbb-token-verify-') as tmp:
            folder, _ = self.build(tmp)
            self.run_verifier(folder)
            before = (folder / 'issuance-status.json').read_text()
            self.assertEqual(self.run_verifier(folder)[0], 1)
            self.assertEqual((folder / 'issuance-status.json').read_text(), before)

    def test_missing_rows(self):
        now = int(time.time())
        self.expect_failing({'row_count', 'subject_sequence'}, rows=[make_token(i, now, 86400) for i in range(1, N)])

    def test_duplicate_row(self):
        now = int(time.time())
        rows = [make_token(i, now, 86400) for i in range(1, N + 1)]
        rows[4] = rows[3]
        self.expect_failing({'unique_rows', 'subject_sequence'}, rows=rows)

    def test_subject_order_swapped(self):
        now = int(time.time())
        rows = [make_token(i, now, 86400) for i in range(1, N + 1)]
        rows[2], rows[3] = rows[3], rows[2]
        self.expect_failing({'subject_sequence'}, rows=rows)

    def test_file_mode_too_open(self):
        self.expect_failing({'file_mode_0600'}, file_mode=0o644)

    def test_dir_mode_too_open(self):
        self.expect_failing({'dir_mode_0700'}, dir_mode=0o755)

    def test_ttl_mismatch(self):
        self.expect_failing({'ttl'}, ttl=2 * 86400, iat_age=0)  # 만료는 충분히 멀고 TTL만 다르다

    def test_expires_too_soon(self):
        self.expect_failing({'remaining'}, iat_age=86400 - 2 * 3600)

    def test_header_wrong(self):
        self.expect_failing({'header_token'}, header='tok')

    def test_users_me_unauthorized(self):
        self.expect_failing({'http_samples'}, server_mode='unauth')

    def test_users_me_wrong_user(self):
        self.expect_failing({'http_samples'}, server_mode='wrong_user')

    def test_blank_row_is_flagged(self):
        now = int(time.time())
        rows = [make_token(i, now, 86400) for i in range(1, N + 1)]
        rows.insert(5, '')
        with tempfile.TemporaryDirectory(prefix='dbb-token-verify-') as tmp:
            folder, _ = self.build(tmp, rows=rows)
            _, failing, out, _ = self.run_verifier(folder)
            self.assertIn('no_empty_row', failing, out)


if __name__ == '__main__':
    unittest.main(verbosity=2)
