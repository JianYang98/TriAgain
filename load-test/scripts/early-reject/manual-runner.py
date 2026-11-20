#!/usr/bin/env python3
"""User-invoked phase command. Default only validates; --execute sends load."""
import pathlib,json,os,sys,subprocess,time,signal,base64,urllib.request
ROOT=pathlib.Path('/home/ec2-user/triagain-k6')
RUNS={'warmup':'20261107-075505-db-50-0613','main':'20261107-075505-db-150-560c'}
assert len(sys.argv) in (2,3) and sys.argv[1] in RUNS
phase=sys.argv[1];execute=len(sys.argv)==3 and sys.argv[2]=='--execute';assert len(sys.argv)==2 or execute
run=RUNS[phase];out=ROOT/'load-test/results/raw/20261107'/run;p=out/'ready-runner.json';c=json.loads(p.read_text())
SSH=['ssh','-i',os.environ['APP_SSH_KEY'],'-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=5',os.environ['APP_SSH_TARGET']]
def app(mode):return subprocess.run(SSH+['python3 /home/ec2-user/early-reject-manual-150-560c.py '+run+' '+mode],check=True)
rows=pathlib.Path(c['tokens_file']).read_text().splitlines()[1:];assert len(rows)==9000 and len(set(rows))==9000
claims=[json.loads(base64.urlsafe_b64decode(r.split('.')[1]+'===')) for r in rows];assert [d['sub'] for d in claims]==['loadtest-user-'+str(i) for i in range(1,9001)];assert min(d['exp'] for d in claims)>time.time()+3600
assert not (out/'.main-c-run-claimed').exists() and not (out/'.warmup-c-run-claimed').exists()
if phase=='main' and execute:
 w=ROOT/'load-test/results/raw/20261107'/RUNS['warmup'];summary=json.loads((w/'warmup-c-summary.json').read_text())['dbc'];execution=json.loads((w/'warmup-c-execution.json').read_text())
 assert summary['load_and_response_checks_passed'] and summary['requests']==1000 and summary['counts']['join_201']==99 and summary['counts']['join_cr002']==901 and execution['k6_exit']==execution['stop_exit']==0
 assert (w/'USER_REVIEWED_WARMUP').is_file(),'Review warmup before main'
if not execute:
 subprocess.run(['python3','-I',str(ROOT/'load-test/scripts/run-early-reject-3ebff4.py'),str(p),'--check'],check=True);print('CHECK_ONLY; no load');sys.exit(0)
# Never run another phase concurrently.
lock=ROOT/'load-test/results/raw/20261107/.manual-early-reject-active';lock.mkdir(mode=0o700)
raw=None;metrics=None;rc=1
try:
 app('pre')
 samples=[]
 for i in range(5):
  t0=time.time_ns()/1000000;remote=subprocess.check_output(SSH+['date +%s%3N'],text=True);t1=time.time_ns()/1000000;samples.append({'rtt_ms':t1-t0,'offset_ms':int(remote)-(t0+t1)/2})
 best=min(samples,key=lambda x:x['rtt_ms']);(out/'clock-check.json').write_text(json.dumps({'samples':samples,'best':best},indent=2));c['clock_offset_ms']=round(best['offset_ms'])
 f=(out/'raw-scrape-console.log').open('xb');raw=subprocess.Popen(['python3','-u','/home/ec2-user/raw-scrape-3ebff4.py',str(out/'raw-metrics'),os.environ['BASE_URL']],stdout=f,stderr=subprocess.STDOUT,start_new_session=True);f.close()
 metrics=out/'runner-metrics';metrics.mkdir(mode=0o700);env=dict(os.environ,RUN_ID=run,OUT_DIR=str(metrics))
 for name in ['mpstat','vmstat']:
  f=(metrics/(name+'.console.log')).open('xb');subprocess.Popen([str(ROOT/'load-test/collectors'/(name+'-run.sh'))],env=env,stdin=subprocess.DEVNULL,stdout=f,stderr=subprocess.STDOUT,start_new_session=True);f.close()
 time.sleep(3);assert raw.poll() is None
 raw_samples=[json.loads(x) for x in (out/'raw-metrics/index.jsonl').read_text().splitlines()];assert len(raw_samples)>=2 and all('file' in x for x in raw_samples)
 for name in ['mpstat','vmstat']:os.kill(int((metrics/(name+'.pid')).read_text().split()[0]),0)
 c['ready']=True;p.write_text(json.dumps(c,indent=2))
 (out/'user-command-start.json').write_text(json.dumps({'run_id':run,'phase':phase,'operator':'user','ms':int(time.time()*1000)}))
 rc=subprocess.run(['python3','-u','-I',str(ROOT/'load-test/scripts/run-early-reject-3ebff4.py'),str(p)]).returncode
 app('post')
 summary=json.loads((out/('warmup-c-summary.json' if phase=='warmup' else 'k6-c-summary.json')).read_text())['dbc'];execution=json.loads((out/('warmup-c-execution.json' if phase=='warmup' else 'c-execution.json')).read_text())
 passed=rc==0 and execution['stop_exit']==0 and summary['load_and_response_checks_passed'] and summary['counts']['join_201']==c['expected_total']-1 and summary['counts']['join_cr002']==c['total']-c['expected_total']+1
 print(json.dumps(summary,indent=2));print('WARMUP_PASS' if passed and phase=='warmup' else 'MAIN_PASS' if passed else 'PHASE_FAILED')
 rc=0 if passed else 2
finally:
 if (out/'raw-metrics').exists():(out/'raw-metrics/stop').touch()
 subprocess.run(SSH+['touch /home/ec2-user/dbb-results/20261107/'+run+('/warmup' if phase=='warmup' else '')+'/ss-300ms.stop'])
 if metrics:
  for name in ['mpstat','vmstat']:
   f=metrics/(name+'.pid')
   if f.exists():
    try:os.kill(int(f.read_text().split()[0]),signal.SIGTERM)
    except ProcessLookupError:pass
 c['ready']=False;p.write_text(json.dumps(c,indent=2));lock.rmdir()
sys.exit(rc)
