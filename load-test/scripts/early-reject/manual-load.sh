#!/usr/bin/env bash
set -euo pipefail
phase=${1:-}
case "$phase" in warmup|main) ;; *) echo "Usage: $0 warmup|main"; exit 2;; esac
ssh -i "${RUNNER_SSH_KEY:?RUNNER_SSH_KEY 필요: load-test/private/aws.env}" -o BatchMode=yes -o StrictHostKeyChecking=yes -o ServerAliveInterval=10 -o ServerAliveCountMax=3 "${RUNNER_SSH_TARGET:?RUNNER_SSH_TARGET 필요: load-test/private/aws.env}" "set -a; . /home/ec2-user/triagain-k6/load-test/aws-runner.env; set +a; python3 -u /home/ec2-user/triagain-k6/load-test/scripts/manual-early-reject-150-560c.py $phase --execute"
