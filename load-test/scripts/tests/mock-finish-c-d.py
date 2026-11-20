#!/usr/bin/env python3
"""후처리 판정을 검증한다. SSH·SCP·DB·Prometheus에는 접속하지 않는다."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import subprocess
import sys
from contextlib import redirect_stdout
from unittest.mock import patch

RATE = int(os.environ.get('TEST_RPS', '1000'))  # main RPS: 1000 또는 200/300/400/500/600
script = Path(__file__).resolve().parents[1] / 'finish-db-c-d.py'
spec = importlib.util.spec_from_file_location('finish_c', script)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
# 원격 Python 코드의 문법도 별도로 검사한다.
compile(module.REMOTE, '<remote-finish-c>', 'exec')
cases = ['success', 'not-full', 'missing-sql', 'hash-mismatch', 'load-failed',
         'collector-active', 'remote-failed', 'identity-mismatch', 'wrong-phase',
         'rate-mismatch', 'bad-rate', 'warmup-rate']
with tempfile.TemporaryDirectory(prefix='dbc-finish-mock-') as temporary:
    for case in cases:
        root = Path(temporary) / case
        run = f'20261028-030000-db-{250 if case == "bad-rate" else RATE}-a1b2'
        out = root / 'load-test/results/raw/20261028' / run
        out.mkdir(parents=True)
        card = dict(run_id=run, phase='warmup' if case == 'warmup-rate' else 'main', crew_id='dbb-c-mock', out_dir=str(out),
                    app='mock-app', db_host='mock.invalid', db_name='mock-db', db_user='mock-user',
                    verify_sql='/mock/22_b_verify.sql')
        summary = dict(run_id=run, crew_id=card['crew_id'], warmup=False, total=10000,
                       target_rps=RATE - 1 if case == 'rate-mismatch' else RATE,
                       load_and_response_checks_passed=case != 'load-failed',
                       counts={'join_201': 999})
        execution = dict(version='C', phase='main', run_id=run, crew_id=card['crew_id'],
                         start={'ms': 1000000000000}, stop_end={'ms': 1000000001000},
                         k6_exit=0, stop_exit=0)
        if case == 'identity-mismatch':
            summary['crew_id'] = 'dbb-other'
        if case == 'wrong-phase':
            summary['warmup'] = True
        (out / 'k6-c-summary.json').write_text(json.dumps({'dbc': summary}))
        (out / 'c-execution.json').write_text(json.dumps(execution))
        config = root / 'config.json'
        config.write_text(json.dumps(card))
        checks = {key: 'OK' for key in ['I1_no_overflow', 'I2_counter_match', 'I3_no_duplicate',
                  'I4_one_leader', 'I5_pool_only', 'I6_full_info', 'I7_k6_match']}
        if case == 'not-full':
            checks['I6_full_info'] = 'NOT_FULL'
        if case == 'missing-sql':
            checks.pop('I7_k6_match')
        raw = b'preserved mock evidence\n'
        digest = hashlib.sha256(raw).hexdigest()
        remote = dict(sql_exit=0, sql_checks=checks, app_log_exit=0, errors=[], active_collectors=[],
                      manifest=[{'path': 'sample.txt', 'sha256': digest}])
        if case == 'collector-active':
            remote['active_collectors'] = ['poll-db']
        calls = []

        def capture(command, timeout, env=None):
            calls.append(command[0])
            if command[0] == 'ssh':
                return (255, '', 'mock failure') if case == 'remote-failed' else (0, json.dumps(remote), '')
            if command[0] == 'scp':
                originals = Path(command[-1])
                originals.mkdir()
                (originals / 'sample.txt').write_bytes(b'changed' if case == 'hash-mismatch' else raw)
                return 0, '', ''
            assert command[0] == 'bash'
            return 0, 'mock Prometheus export', ''

        environment = dict(EC2_RESULTS_ROOT='/mock/results', APP_SSH_KEY='/mock/key',
                           APP_SSH_TARGET='mock.invalid')
        with patch.object(module, 'ROOT', root), patch.object(module, 'capture', capture), patch.dict(os.environ, environment):
            if case in ('identity-mismatch', 'wrong-phase', 'bad-rate', 'warmup-rate'):
                try:
                    module.finish(config)
                    raise AssertionError('Identity/rate mismatch accepted')
                except ValueError:
                    assert calls == []
            else:
                result = module.finish(config)
                assert result == (0 if case == 'success' else 2), (case, result)
                assert calls == ['ssh', 'scp', 'bash']
                before = (out / 'finish-c-local/finish-status.json').read_bytes()
                try:
                    module.finish(config)
                    raise AssertionError('Overwrite accepted')
                except ValueError:
                    assert before == (out / 'finish-c-local/finish-status.json').read_bytes()
                assert calls == ['ssh', 'scp', 'bash']
        print('PASS', case)

# 원격 실행 본문도 가짜 docker/psql 응답으로 실행해 SQL 구분자와 비밀값 비출력을 확인한다.
for wrong_database in (False, True):
    with tempfile.TemporaryDirectory(prefix='dbc-remote-mock-') as temporary:
        called = []
        secret = 'mock-password-must-not-appear'
        database = 'other-db' if wrong_database else 'mock-db'

        def fake_subprocess(command, **kwargs):
            called.append(command[0:2])
            if command[:2] == ['docker', 'inspect']:
                body = [{'Config': {'Env': ['DB_URL=jdbc:postgresql://mock.invalid:5432/' + database,
                         'DB_USERNAME=mock-user', 'DB_PASSWORD=' + secret]}}]
                return subprocess.CompletedProcess(command, 0, json.dumps(body), '')
            if command[0] == 'psql':
                assert command[command.index('-F') + 1] == '\t'
                assert 'default_transaction_read_only=on' in kwargs['env']['PGOPTIONS']
                assert kwargs['env']['PGPASSWORD'] == secret
                rows = '\n'.join(name + '\tOK\tOK' for name in checks)
                return subprocess.CompletedProcess(command, 0, rows, '')
            assert command[:2] == ['docker', 'logs']
            kwargs['stdout'].write(b'mock app logs\n')
            return subprocess.CompletedProcess(command, 0)

        arguments = ['remote', temporary, 'mock-app', 'dbb-c-mock', '999', '1000',
                     'mock.invalid', '5432', 'mock-db', 'mock-user', '/mock/verify.sql']
        output = io.StringIO()
        with patch.object(sys, 'argv', arguments), patch('subprocess.run', fake_subprocess), redirect_stdout(output):
            exec(compile(module.REMOTE, '<remote-finish-c>', 'exec'), {})
        result = json.loads(output.getvalue())
        assert secret not in output.getvalue()
        if wrong_database:
            assert not any(command[0] == 'psql' for command in called)
            assert result['errors'] == ['sql:ValueError']
        else:
            assert len(result['sql_checks']) == 7 and result['errors'] == []
        print('PASS remote DB identity guard' if wrong_database else 'PASS remote SQL parsing, read-only connection, no password output')
