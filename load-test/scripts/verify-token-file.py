#!/usr/bin/env python3
"""새 토큰 폴더의 tokens.csv를 검증하고 issuance-status.json을 쓴다. 토큰 값은 어떤 출력에도 쓰지 않는다.

사용: python3 -I verify-token-file.py <토큰폴더> --base-url http://HOST:8080 [--expected 10000]
        [--ttl-seconds 86400] [--min-remaining-seconds 21600] [--samples 1,5000,10000]
검사: 폴더 0700 · 파일 0600 · 헤더 `token` · 행 수 · 빈 행/중복 없음 · JWT sub 가 loadtest-user-1…N 순서 ·
      exp−iat 가 TTL · 가장 이른 exp 가 지금+여유 이후 · 샘플 행의 GET /users/me 가 200 이고 data.id 가 sub 와 일치.
종료 코드: 0 전부 통과(status=validated) · 2 하나라도 실패(status=failed) · 1 입력 오류.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request


def jwt_payload(token):
    part = token.split('.')[1]
    return json.loads(base64.urlsafe_b64decode(part + '=' * (-len(part) % 4)))


def utc(epoch):
    return datetime.datetime.fromtimestamp(epoch, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S+00:00')


def read_json(path):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return {}


def http_me(base_url, token):
    request = urllib.request.Request(base_url.rstrip('/') + '/users/me', headers={'Authorization': 'Bearer ' + token})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response).get('data', {}).get('id')
    except urllib.error.HTTPError as exc:
        return exc.code, None
    except (OSError, ValueError):
        return 0, None


def check_file(folder, args):
    """파일 자체 검사 — 결과 (checks, rows). 실패 이유는 checks 키 이름으로 구분된다."""
    path = folder / 'tokens.csv'
    checks = {'dir_mode_0700': (folder.stat().st_mode & 0o777) == 0o700,
              'file_mode_0600': path.is_file() and (path.stat().st_mode & 0o777) == 0o600}
    lines = path.read_text(encoding='utf-8').split('\n') if path.is_file() else []
    if lines and lines[-1] == '':
        lines.pop()
    rows = [line.strip() for line in lines[1:]]
    checks['header_token'] = bool(lines) and lines[0].strip() == 'token'
    checks['row_count'] = len(rows) == args.expected
    checks['no_empty_row'] = all(rows)
    checks['unique_rows'] = len(set(rows)) == len(rows)
    return checks, rows


def check_claims(rows, args):
    """JWT 내용 검사 — sub 순서, TTL, 남은 시간. 토큰 값은 쓰지 않고 행 번호만 센다."""
    subjects, ttls, expiries = [], set(), []
    for token in rows:
        try:
            claims = jwt_payload(token)
            subjects.append(claims['sub'])
            ttls.add(claims['exp'] - claims['iat'])
            expiries.append(claims['exp'])
        except (IndexError, KeyError, ValueError, TypeError):
            subjects.append(None)
    min_exp = min(expiries) if expiries else 0
    checks = {'jwt_decodable': None not in subjects and len(expiries) == len(rows),
              'subject_sequence': subjects == ['loadtest-user-%d' % i for i in range(1, len(rows) + 1)]
              and len(rows) == args.expected,
              'ttl': ttls == {args.ttl_seconds},
              'remaining': min_exp - time.time() >= args.min_remaining_seconds}
    return checks, subjects, min_exp


def check_samples(rows, subjects, args):
    """샘플 행의 /users/me — 인증 200 과 사용자 일치."""
    samples = []
    for number in args.samples:
        if not 1 <= number <= len(rows):
            samples.append({'row': number, 'http': None, 'user_match': False})
            continue
        status, user_id = http_me(args.base_url, rows[number - 1])
        samples.append({'row': number, 'http': status, 'user_match': user_id == subjects[number - 1]})
    return {'http_samples': bool(samples) and all(s['http'] == 200 and s['user_match'] for s in samples)}, samples


def build_status(folder, args, checks, samples, rows, min_exp):
    times = {**read_json(folder / 'issuance-times.json'), **read_json(folder / 'issuance-end.json')}
    log = (folder / 'issuance.log').read_text() if (folder / 'issuance.log').is_file() else ''
    done = re.search(r'완료: 성공=(\d+), 실패=(\d+)', log)
    script = folder / 'bin' / 'generate-tokens.sh'
    elapsed = times['ended_epoch'] - times['started_epoch'] if 'ended_epoch' in times and 'started_epoch' in times else None
    return {'status': 'validated' if all(checks.values()) else 'failed',
            'success': int(done.group(1)) if done else None, 'failure': int(done.group(2)) if done else None,
            'expected': args.expected, 'token_file': str(folder / 'tokens.csv'),
            'file_mode': oct((folder / 'tokens.csv').stat().st_mode & 0o777),
            'sha256': hashlib.sha256((folder / 'tokens.csv').read_bytes()).hexdigest(),
            'started_utc': times.get('started_utc'), 'ended_utc': times.get('ended_utc'), 'elapsed_seconds': elapsed,
            'issuer_exit': times.get('exit'), 'min_expiry_utc': utc(min_exp) if min_exp else None,
            'remaining_seconds': int(min_exp - time.time()) if min_exp else None,
            'subject_sequence_valid': checks.get('subject_sequence'),
            'duplicate_count': len(rows) - len(set(rows)), 'authenticated_samples': samples,
            'token_values_printed': False, 'checks': checks,
            'issuer': 'copy of generate-tokens.sh' + (' sha256=' + hashlib.sha256(script.read_bytes()).hexdigest()[:16]
                                                      if script.is_file() else ''),
            'strategy_context': 'DB Only C token issuance only'}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('folder')
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--expected', type=int, default=10000)
    parser.add_argument('--ttl-seconds', type=int, default=86400)
    parser.add_argument('--min-remaining-seconds', type=int, default=6 * 3600)
    parser.add_argument('--samples', type=lambda v: [int(x) for x in v.split(',') if x], default=[1, 5000, 10000])
    args = parser.parse_args(argv)
    folder = Path(args.folder).resolve()
    if not (folder / 'tokens.csv').is_file() or (folder / 'issuance-status.json').exists():
        print('FAIL input: tokens.csv 없음 또는 issuance-status.json 이 이미 있다(덮어쓰지 않는다)')
        return 1
    os.umask(0o077)
    checks, rows = check_file(folder, args)
    claim_checks, subjects, min_exp = check_claims(rows, args)
    sample_checks, samples = (check_samples(rows, subjects, args) if claim_checks['jwt_decodable']
                              else ({'http_samples': False}, []))
    checks.update(claim_checks)
    checks.update(sample_checks)
    status = build_status(folder, args, checks, samples, rows, min_exp)
    with (folder / 'issuance-status.json').open('x') as handle:
        json.dump(status, handle, indent=2)
        handle.write('\n')
    for name, passed in checks.items():
        print('%-18s %s' % (name, 'PASS' if passed else 'FAIL'))
    print('rows=%d unique=%d min_expiry=%s remaining_seconds=%s status=%s' % (
        len(rows), len(set(rows)), status['min_expiry_utc'], status['remaining_seconds'], status['status']))
    for sample in samples:
        print('sample row=%d http=%s user_match=%s' % (sample['row'], sample['http'], sample['user_match']))
    return 0 if status['status'] == 'validated' else 2


if __name__ == '__main__':
    sys.exit(main())
