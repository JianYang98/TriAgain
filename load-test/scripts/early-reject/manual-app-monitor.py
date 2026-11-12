#!/usr/bin/env python3
"""Per-run monitoring setup and read-only checks. Contains no join requests."""
import pathlib,subprocess,json,os,urllib.parse,urllib.request,time,sys
os.umask(0o077)
run,mode=sys.argv[1:3]
assert run in ['20261107-075505-db-50-0613','20261107-075505-db-150-560c'] and mode in ['pre','post']
out=pathlib.Path('/home/ec2-user/dbb-results/20261107')/run
c=json.loads((out/'manual-phase.json').read_text());out=out/'warmup' if c['phase']=='warmup' else out;app=c['app'];d=json.loads(subprocess.check_output(['docker','inspect',app]))[0]
e=dict(x.split('=',1) for x in d['Config']['Env']);u=urllib.parse.urlparse(e['DB_URL'].replace('jdbc:','',1))
assert c['db']['host'] and u.hostname==c['db']['host'] and u.path=='/triagain_dbb' and e['DB_USERNAME']=='triagain_dbb_app'
pg=dict(os.environ,PGHOST=u.hostname,PGPORT='5432',PGDATABASE='triagain_dbb',PGUSER=e['DB_USERNAME'],PGPASSWORD=e['DB_PASSWORD'],PGSSLMODE='require',PGCONNECT_TIMEOUT='5')
def state():
 sql="SELECT json_build_object('max_members',c.max_members,'counter',c.current_members,'rows',count(m.id),'distinct',count(DISTINCT m.user_id),'leaders',count(*) FILTER(WHERE m.role='LEADER'),'outsiders',count(*) FILTER(WHERE m.role<>'LEADER' AND m.user_id NOT LIKE 'loadtest-user-%'),'status',c.status,'join_open',c.start_date>CURRENT_DATE) FROM crews c LEFT JOIN crew_members m ON m.crew_id=c.id WHERE c.id='"+c['crew_id']+"' GROUP BY c.id,c.max_members,c.current_members,c.status,c.start_date;"
 return json.loads(subprocess.check_output(['psql','-X','-w','-At','-v','ON_ERROR_STOP=1','-c',sql],env=dict(pg,PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=10000'),text=True))
def scrape(name):
 with urllib.request.urlopen('http://127.0.0.1:8080/actuator/prometheus',timeout=5) as r:assert r.status==200;(out/name).write_bytes(r.read())
if mode=='pre':
 assert not (out/'monitoring-start.json').exists(),'No reuse'
 assert d['State']['Running'] and '--server.tomcat.accept-count=100' in d['Config']['Cmd']
 s=state();assert s['max_members']==c['expected_total'] and s['counter']==s['rows']==s['distinct']==s['leaders']==1 and s['outsiders']==0 and s['join_open'];(out/'sql-before.json').write_text(json.dumps(s,indent=2))
 req=urllib.request.Request('http://127.0.0.1:8080/internal/gc',data=b'',headers={'X-Internal-Api-Key':e['INTERNAL_API_KEY']})
 with urllib.request.urlopen(req,timeout=30) as r:assert r.status==200;(out/'pre-gc.json').write_bytes(r.read())
 time.sleep(5);scrape('metrics-before.prom')
 pg.update(RUN_ID=run,ARM='db',CREW_ID=c['crew_id'],EXPECTED_TOTAL=str(c['expected_total']),APP_CONTAINER=app,OUT_DIR=str(out),MAX_WAIT_SEC='60')
 r=subprocess.run(['/home/ec2-user/dbb-collectors/box-app-start.sh'],env=pg,capture_output=True,text=True);(out/'collectors-start.txt').write_text(r.stdout+r.stderr);assert r.returncode==0,r.stdout+r.stderr
 stdout=(out/'ss-300ms.jsonl').open('xb');stderr=(out/'ss-300ms.stderr').open('xb')
 p=subprocess.Popen(['sudo','-n','nsenter','-t',str(d['State']['Pid']),'-n','python3','-u','/home/ec2-user/ss-300ms-3ebff4.py',run,str(out/'ss-300ms.stop')],stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);stdout.close();stderr.close();(out/'ss-300ms.pid').write_text(str(p.pid));time.sleep(1)
 assert p.poll() is None
 rows=[json.loads(x) for x in (out/'ss-300ms.jsonl').read_text().splitlines()];assert len(rows)>=3 and all(r['listen_rc']==r['syn_rc']==0 and r['accept_queues'] for r in rows)
 (out/'monitoring-start.json').write_text(json.dumps({'run_id':run,'ms':int(time.time()*1000),'app_started_at':d['State']['StartedAt'],'ss_interval_ms':300},indent=2));print('MONITORING_READY',run)
else:
 (out/'ss-300ms.stop').touch();s=state();(out/'sql-after.json').write_text(json.dumps(s,indent=2));scrape('metrics-after.prom')
 expected=c['expected_total'];passed=s['counter']==s['rows']==s['distinct']==expected and s['max_members']==expected and s['leaders']==1 and s['outsiders']==0
 (out/'sql-verdict.json').write_text(json.dumps({'run_id':run,'passed':passed,'state':s},indent=2));print('DB_PASS' if passed else 'DB_FAIL',run,json.dumps(s));assert passed
 # Preserve final JFR and flushed access logs after the main run. Prometheus is untouched.
 if c['phase']=='main':
  subprocess.run(['docker','stop','--time','30',app],check=True,timeout=45)
  evidence=out/'stage-evidence';evidence.mkdir(mode=0o700)
  with (evidence/'app.log').open('xb') as f:subprocess.run(['docker','logs','--timestamps',app],stdout=f,stderr=subprocess.STDOUT,check=True,timeout=30)
  subprocess.run(['docker','cp',app+':/tmp/db200-cpu-profile.jfr',str(evidence/'final.jfr')],check=True,timeout=30)
  for mount in d['Mounts']:
   if mount['Destination']=='/access-logs':
    import shutil
    shutil.copytree(mount['Source'],evidence/'access')
  after=json.loads(subprocess.check_output(['docker','inspect',app]))[0];(evidence/'container-state.json').write_text(json.dumps(after['State'],indent=2))
  print('APP_STOPPED_AND_EVIDENCE_PRESERVED',run)
