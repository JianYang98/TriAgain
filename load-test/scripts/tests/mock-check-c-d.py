#!/usr/bin/env python3
"""No EC2, DB or actual k6. Isolated fake executables and result directories."""
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time

HERE = Path(tempfile.mkdtemp(prefix='dbb-mock-evidence-'))
RUNNER = Path(__file__).resolve().parents[1] / 'run-db-c-d.py'
tmp = Path(tempfile.mkdtemp(prefix='db50-runner-mock-'))
bin_dir = tmp / 'bin'; bin_dir.mkdir()
fake = '''#!/usr/bin/env python3
import os,sys,time,signal,json
from pathlib import Path
if Path(sys.argv[0]).name == 'ssh':
    print('MOCK C8 stop: '+sys.argv[-1])
    sys.exit(int(os.environ.get('MOCK_STOP_RC','0')))
out = Path(next(x.split('=',1)[1] for x in sys.argv if x.startswith('OUT_DIR=')))
warm='WARMUP=1' in sys.argv
tag='warmup-k6-c' if warm else 'k6-c'
summary='warmup-c-summary.json' if warm else 'k6-c-summary.json'
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
                 'm.ROOT=Path(sys.argv[2])\n'
                 'import resource,json\n'
                 'before=resource.getrlimit(resource.RLIMIT_NOFILE)\n'
                 'rc=m.main(sys.argv[1])\n'
                 'assert before==resource.getrlimit(resource.RLIMIT_NOFILE)\n'
                 'sys.exit(rc)\n')
results=[]
phase=os.environ.get('TEST_PHASE','main')
rate=int(os.environ.get('TEST_RPS','1000'))
for case in ['success','failure','interrupt','stop-failure']:
    root=tmp/case
    run=f'20261014-120000-db-{rate}-a1b2'
    out=root/'load-test/results/raw/20261014'/run; out.mkdir(parents=True)
    tokens=root/'tokens.csv'; tokens.write_text('mock only');tokens.chmod(0o600)
    config=root/'ready.json'; config.write_text(json.dumps(dict(ready=True,phase=phase,run_id=run,target_rps=rate,
        pre_vus=10000 if phase=='main' else 150,max_vus=10000 if phase=='main' else 250,
        crew_id='dbb-db50-01',clock_offset_ms=14,out_dir=str(out),tokens_file=str(tokens))))
    env=dict(os.environ,APP_SSH_KEY='/private/mock-key',APP_SSH_TARGET='test-user@test-app.invalid',BASE_URL='http://test-app.invalid:8080',EC2_RESULTS_ROOT='/tmp/dbb-mock-results',EC2_COLLECTORS_DIR='/tmp/dbb-mock-collectors',PATH=str(bin_dir)+os.pathsep+os.environ['PATH'],
             MOCK_MODE=case,MOCK_STOP_RC='255' if case=='stop-failure' else '0')
    command=[sys.executable,str(entry),str(config),str(root)]
    p=subprocess.Popen(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if case=='interrupt':
        deadline=time.monotonic()+5
        while time.monotonic()<deadline:
            if (out/('warmup-k6-c-console.log' if phase=='warmup' else 'k6-c-console.log')).exists() and 'started' in (out/('warmup-k6-c-console.log' if phase=='warmup' else 'k6-c-console.log')).read_text():break
            time.sleep(.01)
        else: raise AssertionError('mock did not start')
        p.send_signal(signal.SIGINT)
    stdout=p.communicate(timeout=10)[0]
    report=json.loads((out/('warmup-c-execution.json' if phase=='warmup' else 'c-execution.json')).read_text())
    expected=42 if case=='failure' else 130 if case=='interrupt' else 255 if case=='stop-failure' else 0
    assert p.returncode==expected,(case,p.returncode,stdout)
    assert report['stop_exit']==(255 if case=='stop-failure' else 0)
    fractional='1' if phase=='main' and rate in (100,150,200,300,400,500,600) else '0'
    assert all(x in report['k6_command'] for x in [f'PRE_VUS={10000 if phase=="main" else 150}',f'MAX_VUS={10000 if phase=="main" else 250}',f'TARGET_RPS={rate}',
        'TOTAL='+('10000' if phase=='main' else '1000'),f'ALLOW_FRACTIONAL_ARRIVAL={fractional}'])
    stoplog=(out/('warmup-c8-stop.log' if phase=='warmup' else 'c8-stop.log')).read_text()
    assert stoplog.count('MOCK C8 stop:')==1
    assert f'K6_END_MS={report["k6_end"]["ms"]}' in stoplog
    assert report['stop_start']['ms']>=report['k6_end']['ms']
    assert ('/warmup ' in stoplog)==(phase=='warmup')
    assert all((p.stat().st_mode&0o777)==0o600 for p in out.iterdir() if p.is_file())
    before={p.name:p.read_bytes() for p in out.iterdir() if p.is_file()}
    retry=subprocess.run(command,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    assert retry.returncode!=0
    assert before=={p.name:p.read_bytes() for p in out.iterdir() if p.is_file()}
    results.append(dict(case=case,status='PASS',exit=p.returncode,
        k6_exit=report['k6_exit'],stop_exit=report['stop_exit'],
        end_to_stop_ms=report['stop_start']['ms']-report['k6_end']['ms'],
        no_overwrite='PASS',private_files='PASS'))
# --check 모드로 RPS 허용 범위·RUN_ID 일치를 검사한다 (k6·ssh 실행 없음, 결과 파일도 만들지 않음).
import contextlib, importlib.util, io
os.environ.update(APP_SSH_KEY='/private/mock-key',APP_SSH_TARGET='test-user@test-app.invalid',BASE_URL='http://test-app.invalid:8080',
                  EC2_RESULTS_ROOT='/tmp/dbb-mock-results',EC2_COLLECTORS_DIR='/tmp/dbb-mock-collectors')
spec=importlib.util.spec_from_file_location('runner_d',RUNNER); runner=importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
matrix=[]
for name,ph,target,run_rate,ok in [
        ('main-1000','main',1000,1000,True),('warmup-50','warmup',50,50,True),
        *[(f'main-{r}','main',r,r,True) for r in (100,150,200,300,400,500,600)],
        ('main-125','main',125,125,False),('main-250','main',250,250,False),('main-700','main',700,700,False),('main-800','main',800,800,False),('main-0','main',0,0,False),
        ('main-float-200','main',200.0,200,False),('warmup-200','warmup',200,200,False),('warmup-1000','warmup',1000,1000,False),
        ('run-id-rate-mismatch','main',200,300,False)]:
    root=tmp/('check-'+name)
    run=f'20261014-120000-db-{run_rate}-a1b2'
    out=root/'load-test/results/raw/20261014'/run; out.mkdir(parents=True)
    tokens=root/'tokens.csv'; tokens.write_text('mock only'); tokens.chmod(0o600)
    config=root/'ready.json'; config.write_text(json.dumps(dict(ready=False,phase=ph,run_id=run,target_rps=target,
        pre_vus=10000 if ph=='main' else 150,max_vus=10000 if ph=='main' else 250,
        crew_id='dbb-db50-01',clock_offset_ms=14,out_dir=str(out),tokens_file=str(tokens))))
    runner.ROOT=root
    buffer=io.StringIO()
    try:
        with contextlib.redirect_stdout(buffer): rc=runner.main(str(config),True)
        accepted=(rc==0 and 'C config PASS' in buffer.getvalue() and f'RPS={target}' in buffer.getvalue())
    except ValueError:
        accepted=False
    assert accepted==ok,(name,accepted,buffer.getvalue())
    assert not any(out.iterdir()),(name,'check mode must not create result files')
    matrix.append(dict(case=name,accepted=accepted,expected=ok,status='PASS'))
report={'scope':'MOCK ONLY: no network, EC2, DB, real k6 or collectors',
        'evidence_dir':str(tmp),'results':results,'check_matrix':matrix}
(HERE/(f'mock-result-{rate}-{phase}.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
