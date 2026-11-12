#!/usr/bin/env python3
"""RD copy of ../2026-10-14-185842-db50-manual-48404/mock-check.py, pointed at run-rd.py.
No EC2, DB, Redis or actual k6. Isolated fake executables and result directories.

Differences from the original (everything else is identical):
  - imports run-rd.py, run ids carry "-rd-", crew id dbb-rd<rps>-01, result file mock-result-rd-<rps>-<phase>.json
  - fake ssh flushes its marker line and can wait (MOCK_MODE=stop-wait) to imitate the long drain wait
  - extra case 'interrupt-during-stop': SIGINT while the stop call is still running
  - every case also asserts the REMINDER line that run-rd.py prints (run-db50.py has no such line)
Usage: TEST_PHASE=main|warmup TEST_RPS=50|200 python3 -I mock-check-rd.py
"""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time

HERE = Path(tempfile.mkdtemp(prefix='dbb-mock-evidence-'))
RUNNER = Path(__file__).resolve().parents[1] / 'run-rd.py'
tmp = Path(tempfile.mkdtemp(prefix='rd-runner-mock-'))
bin_dir = tmp / 'bin'; bin_dir.mkdir()
fake = '''#!/usr/bin/env python3
import os,sys,time,signal,json
from pathlib import Path
if Path(sys.argv[0]).name == 'ssh':
    print('MOCK C8 stop: '+sys.argv[-1],flush=True)
    if os.environ.get('MOCK_MODE')=='stop-wait':
        time.sleep(float(os.environ.get('MOCK_STOP_SLEEP','3')))
    sys.exit(int(os.environ.get('MOCK_STOP_RC','0')))
out = Path(next(x.split('=',1)[1] for x in sys.argv if x.startswith('OUT_DIR=')))
warm='WARMUP=1' in sys.argv
tag='warmup-k6' if warm else 'k6'
summary='warmup-summary.json' if warm else 'k6-summary.json'
(out/(tag+'.json')).write_text('{}\\n')
print('MOCK k6 started',flush=True)
if os.environ['MOCK_MODE']=='interrupt':
    def done(s,f):
        (out/summary).write_text('{}')
        print('MOCK interrupted; partial summary preserved',flush=True)
        sys.exit(130)
    signal.signal(signal.SIGINT,done)
    while True: time.sleep(.01)
(out/summary).write_text('{}')
sys.exit(42 if os.environ['MOCK_MODE']=='failure' else 0)
'''
for name in ['k6', 'ssh']:
    p=bin_dir/name; p.write_text(fake); p.chmod(0o700)
entry=tmp/'entry.py'
entry.write_text('import importlib.util,sys\nfrom pathlib import Path\n'
                 f's=importlib.util.spec_from_file_location("runner",{str(RUNNER)!r})\n'
                 'm=importlib.util.module_from_spec(s);s.loader.exec_module(m)\n'
                 'm.ROOT=Path(sys.argv[2]);sys.exit(m.main(sys.argv[1]))\n')
results=[]
phase=os.environ.get('TEST_PHASE','main')
rate=int(os.environ.get('TEST_RPS','50'))
console=lambda out: out/('warmup-k6-console.log' if phase=='warmup' else 'k6-console.log')
stoplog=lambda out: out/('warmup-c8-stop.log' if phase=='warmup' else 'c8-stop.log')
for case in ['success','failure','interrupt','stop-failure','interrupt-during-stop']:
    root=tmp/case
    run=f'20261014-120000-rd-{rate}-a1b2'
    out=root/'load-test/results/raw/20261014'/run; out.mkdir(parents=True)
    tokens=root/'tokens.csv'; tokens.write_text('mock only');tokens.chmod(0o600)
    config=root/'ready.json'; config.write_text(json.dumps(dict(ready=True,phase=phase,run_id=run,target_rps=rate,
        pre_vus=1000 if rate==200 else 150,max_vus=1000 if rate==200 else 250,
        crew_id=f'dbb-rd{rate}-01',clock_offset_ms=14,out_dir=str(out),tokens_file=str(tokens))))
    mode='stop-wait' if case=='interrupt-during-stop' else case
    env=dict(os.environ,APP_SSH_KEY='/private/mock-key',APP_SSH_TARGET='test-user@test-app.invalid',BASE_URL='http://test-app.invalid:8080',EC2_RESULTS_ROOT='/tmp/dbb-mock-results',EC2_COLLECTORS_DIR='/tmp/dbb-mock-collectors',PATH=str(bin_dir)+os.pathsep+os.environ['PATH'],
             MOCK_MODE=mode,MOCK_STOP_RC='255' if case=='stop-failure' else '0')
    command=[sys.executable,str(entry),str(config),str(root)]
    p=subprocess.Popen(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if case=='interrupt':
        deadline=time.monotonic()+5
        while time.monotonic()<deadline:
            if console(out).exists() and 'started' in console(out).read_text():break
            time.sleep(.01)
        else: raise AssertionError('mock did not start')
        p.send_signal(signal.SIGINT)
    if case=='interrupt-during-stop':
        deadline=time.monotonic()+5
        while time.monotonic()<deadline:
            if stoplog(out).exists() and 'MOCK C8 stop' in stoplog(out).read_text():break
            time.sleep(.01)
        else: raise AssertionError('mock stop did not start')
        p.send_signal(signal.SIGINT)   # k6 already exited; only the stop call is running
    stdout=p.communicate(timeout=15)[0]
    report=json.loads((out/('warmup-manual-execution.json' if phase=='warmup' else 'manual-execution.json')).read_text())
    expected=42 if case=='failure' else 130 if case=='interrupt' else 255 if case=='stop-failure' else 0
    assert p.returncode==expected,(case,p.returncode,stdout)
    assert report['stop_exit']==(255 if case=='stop-failure' else 0)
    assert all(x in report['k6_command'] for x in [f'PRE_VUS={1000 if rate==200 else 150}',f'MAX_VUS={1000 if rate==200 else 250}',f'TARGET_RPS={rate}'])
    text=stoplog(out).read_text()
    assert text.count('MOCK C8 stop:')==1
    assert f'K6_END_MS={report["k6_end"]["ms"]}' in text
    assert report['stop_start']['ms']>=report['k6_end']['ms']
    assert ('/warmup ' in text)==(phase=='warmup')
    assert b'REMINDER (manual, Redis arm)' in stdout,(case,stdout)
    assert all((p.stat().st_mode&0o777)==0o600 for p in out.iterdir() if p.is_file())
    extra={}
    if case=='interrupt-during-stop':
        # Ctrl-C while the (long) drain wait is running is recorded but does not abort the wait
        assert report['signals']==[signal.SIGINT.value],report['signals']
        waited=report['stop_end']['ms']-report['stop_start']['ms']
        assert waited>=2500,waited
        extra=dict(signals=report['signals'],stop_wait_ms=waited)
    before={p.name:p.read_bytes() for p in out.iterdir() if p.is_file()}
    retry=subprocess.run(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    assert retry.returncode!=0
    assert before=={p.name:p.read_bytes() for p in out.iterdir() if p.is_file()}
    results.append(dict(case=case,status='PASS',exit=p.returncode,
        k6_exit=report['k6_exit'],stop_exit=report['stop_exit'],
        end_to_stop_ms=report['stop_start']['ms']-report['k6_end']['ms'],
        reminder='PASS',no_overwrite='PASS',private_files='PASS',**extra))
report={'scope':'MOCK ONLY: no network, EC2, DB, Redis, real k6 or collectors',
        'target':'run-rd.py','phase':phase,'target_rps':rate,
        'evidence_dir':str(tmp),'results':results}
(HERE/(f'mock-result-rd-{rate}-{phase}.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
