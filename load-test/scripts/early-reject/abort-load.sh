#!/usr/bin/env bash
set -euo pipefail
phase=${1:-}
case "$phase" in warmup) RUN=20261107-075505-db-50-0613;; main) RUN=20261107-075505-db-150-560c;; *) echo "Usage: $0 warmup|main"; exit 2;; esac
ssh -i "${RUNNER_SSH_KEY:?RUNNER_SSH_KEY 필요: load-test/private/aws.env}" -o BatchMode=yes -o StrictHostKeyChecking=yes "${RUNNER_SSH_TARGET:?RUNNER_SSH_TARGET 필요: load-test/private/aws.env}" "RUN_TO_STOP=$RUN python3 -" <<'REMOTE'
import pathlib,os,signal
run=os.environ['RUN_TO_STOP'];found=[]
for p in pathlib.Path('/proc').iterdir():
 if not p.name.isdigit():continue
 try:args=p.joinpath('cmdline').read_bytes().split(b'\0')
 except OSError:continue
 if any(a.endswith(b'/run-early-reject-3ebff4.py') for a in args) and any(a.endswith(('/'+run+'/ready-runner.json').encode()) for a in args):
  os.kill(int(p.name),signal.SIGINT);found.append(int(p.name))
print('SIGINT sent to this run only:',found)
REMOTE
