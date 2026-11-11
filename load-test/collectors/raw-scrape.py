#!/usr/bin/env python3
"""Save bounded, timestamped raw app metrics; no credentials or join requests."""
import json, pathlib, sys, time, urllib.request, os
os.umask(0o077)
out=pathlib.Path(sys.argv[1]); url=sys.argv[2]
out.mkdir(mode=0o700,exist_ok=False)
(out/'collector.pid').write_text(str(os.getpid()))
start=time.monotonic()
with (out/'index.jsonl').open('x') as index:
 for i in range(300):
  if (out/'stop').exists(): break
  stamp=int(time.time()*1000)
  try:
   with urllib.request.urlopen(url+'/actuator/prometheus',timeout=3) as response:
    data=response.read(); assert response.status==200
   name=f'{i:04d}-{stamp}.prom';(out/name).write_bytes(data)
   entry={'ms':stamp,'file':name,'bytes':len(data)}
  except Exception as exc:entry={'ms':stamp,'error':type(exc).__name__}
  index.write(json.dumps(entry)+'\n');index.flush()
  time.sleep(max(0,start+i+1-time.monotonic()))
(out/'finished.json').write_text(json.dumps({'ended_ms':int(time.time()*1000)}))
