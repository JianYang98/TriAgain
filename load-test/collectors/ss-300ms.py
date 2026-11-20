#!/usr/bin/env python3
"""Read accept queue and SYN-RECV in the app netns, nominally every 300ms."""
import json,pathlib,subprocess,sys,time
run,stop=sys.argv[1:];stop=pathlib.Path(stop);deadline=time.monotonic();started=deadline
for i in range(1200):
 if stop.exists():break
 sample_start=time.time_ns()//1000000
 listen=subprocess.run(['ss','-H','-lnt','sport = :8080'],capture_output=True,text=True,timeout=2)
 syn=subprocess.run(['ss','-H','-nt','state','syn-recv','sport = :8080'],capture_output=True,text=True,timeout=2)
 queues=[]
 for line in listen.stdout.splitlines():
  cols=line.split()
  if len(cols)>=5:queues.append({'recv_q':int(cols[1]),'send_q':int(cols[2]),'local':cols[3]})
 print(json.dumps({'run_id':run,'sample':i,'start_ms':sample_start,'end_ms':time.time_ns()//1000000,
  'listen_rc':listen.returncode,'syn_rc':syn.returncode,'accept_queues':queues,
  'syn_recv':len(syn.stdout.splitlines()),'listen_raw':listen.stdout,'syn_raw':syn.stdout}),flush=True)
 deadline+=.3
 if deadline<time.monotonic():deadline=time.monotonic()
 time.sleep(max(0,deadline-time.monotonic()))
