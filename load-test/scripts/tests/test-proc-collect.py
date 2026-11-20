#!/usr/bin/env python3
"""합성 /proc만 사용하는 로컬 점검. 표준 라이브러리만 사용, 서버·컨테이너·부하 실행 없음."""
import os
import pathlib
import re
import subprocess
import tempfile
import time

COLLECTORS = pathlib.Path(__file__).resolve().parents[2] / 'collectors'


def invoke(mode, env):
    return subprocess.run(['/bin/bash', str(COLLECTORS / 'proc-collect.sh'), mode],
                          env=env, capture_output=True, text=True, timeout=3)


def collect(mode, env, out, absent=False):
    proc = subprocess.Popen(['/bin/bash', str(COLLECTORS / 'proc-collect.sh'), mode],
                            env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    try:
        path = out / (mode + '.log')
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            if path.exists():
                lines = path.read_text().splitlines()
                # PSI도 같은 자원(memory)의 서로 다른 두 tick을 기다린다.
                samples = [line for line in lines if mode != 'pressure' or ' memory ' in line]
                if len({line.split()[0] for line in samples}) >= 2:
                    break
            assert proc.poll() is None, 'collector exited before two ticks'
            time.sleep(0.05)
        else:
            raise AssertionError('two samples were not collected')
        assert invoke(mode, env).returncode == 1, 'duplicate start accepted'
        proc.terminate()
        _, err = proc.communicate(timeout=4)
        assert proc.returncode == 0, err
        lines = path.read_text().splitlines()
        assert all(re.match(r'^\d{13} test-run ', line) for line in lines), lines
        assert lines[-1].endswith('STOP signal=TERM'), lines[-1]
        assert not (out / (mode + '.pid')).exists(), 'PID not cleaned up'
        before = path.read_bytes()
        assert invoke(mode, env).returncode == 1, 'existing log accepted'
        assert path.read_bytes() == before, 'existing log overwritten'
        if absent:
            assert all('없음' in line for line in lines[:-1]), lines
        print('PASS: ' + mode + ' — two ticks, epoch ms, TERM/STOP, PID cleanup, duplicate/overwrite guard')
        return '\n'.join(lines[:-1])
    finally:
        if proc.poll() is None:
            proc.terminate()
        proc.communicate(timeout=4)


def main():
    with tempfile.TemporaryDirectory(prefix='proc-collect-test-') as tmp:
        root = pathlib.Path(tmp)
        proc = root / 'proc'
        out = root / 'out'
        out.mkdir()
        fixtures = {
            'vmstat': 'pgfault 111\npgmajfault 2\npgpgin 3\npgpgout 4\npswpin 5\npswpout 6\n'
                      'pgscan_kswapd 7\npgsteal_direct 8\nnr_free_pages 99\n',
            'sys/net/netfilter/nf_conntrack_count': '17\n',
            'sys/net/netfilter/nf_conntrack_max': '7680\n',
            'diskstats': ' 259 0 synthetic0 1 2 3 4 5 6 7 8 9 10 11\n',
        }
        for resource in ('memory', 'io', 'cpu'):
            fixtures['pressure/' + resource] = 'some avg10=0.01 avg60=0.02 avg300=0.03 total=12\n'
        for name, value in fixtures.items():
            path = proc / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
        env = dict(os.environ, PROC_ROOT=str(proc), OUT_DIR=str(out), RUN_ID='test-run')
        pressure = collect('pressure', env, out)
        for resource in ('memory', 'io', 'cpu'):
            assert resource + ' some avg10=0.01' in pressure
        vmstat = collect('procvmstat', env, out)
        for field in ('pgfault=111', 'pgmajfault=2', 'pgpgin=3', 'pgpgout=4', 'pswpin=5',
                      'pswpout=6', 'pgscan_kswapd=7', 'pgsteal_direct=8'):
            assert field in vmstat, field
        assert 'nr_free_pages' not in vmstat
        assert 'count=17 max=7680' in collect('conntrack', env, out)
        assert '259 0 synthetic0 1 2 3 4 5 6 7 8 9 10 11' in collect('diskstats', env, out)
        for path in (proc / 'pressure').iterdir():
            path.unlink()
        absent = root / 'absent-psi'
        absent.mkdir()
        collect('pressure', dict(env, OUT_DIR=str(absent)), absent, absent=True)
        print('PASS: missing PSI records 없음 and continues')
        missing = root / 'missing'
        missing.mkdir()
        for mode, name in (('procvmstat', 'vmstat'), ('conntrack', 'sys/net/netfilter/nf_conntrack_count'),
                           ('diskstats', 'diskstats')):
            (proc / name).unlink()
            result = invoke(mode, dict(env, OUT_DIR=str(missing)))
            assert result.returncode == 3, result.stderr
            assert not (missing / (mode + '.pid')).exists()
        print('PASS: missing non-PSI sources warn with code 3')
    print('PASS: proc collectors local check')


if __name__ == '__main__':
    main()
