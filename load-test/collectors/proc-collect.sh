#!/usr/bin/env bash
# 호스트 /proc 읽기 전용 1초 수집. bash 3.2/5.x, sudo·설치 없음.
# procvmstat 허용 키: pgfault pgmajfault pgpgin pgpgout pswpin pswpout pgscan* pgsteal*.
# 누적 카운터를 기록하며 증분은 후처리한다. diskstats는 디바이스별 전체 필드.
set -euo pipefail
USAGE='사용법: RUN_ID=… OUT_DIR=… [PROC_ROOT=/proc] proc-collect.sh pressure|procvmstat|conntrack|diskstats
  출력: <모드>.log / <모드>.pid. 각 행은 <UTC epoch ms> <run-id> ….
  PSI 미지원은 없음으로 기록하고 계속 수집. 그 외 원천을 읽을 수 없으면 코드 3(비차단).
  SIGTERM/INT는 STOP 줄을 남기고 종료. 기존 로그·실행 중 PID는 덮어쓰지 않는다.'
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=common.sh
source "$DIR/common.sh"
[[ $# == 1 ]] || usage
MODE=$1
case "$MODE" in pressure|procvmstat|conntrack|diskstats) ;; *) usage ;; esac
require_env RUN_ID OUT_DIR
require_id RUN_ID
PROC_ROOT=${PROC_ROOT:-/proc}
LOG=$OUT_DIR/$MODE.log
start_guard "$MODE" "$LOG"
warn() { die 3 "$MODE: 원천을 읽을 수 없음 — 비차단, 설치·권한 변경 없이 진행"; }
case "$MODE" in
procvmstat) [[ -r "$PROC_ROOT/vmstat" ]] || warn ;;
conntrack)
	[[ -r "$PROC_ROOT/sys/net/netfilter/nf_conntrack_count" &&
	   -r "$PROC_ROOT/sys/net/netfilter/nf_conntrack_max" ]] || warn ;;
diskstats) [[ -r "$PROC_ROOT/diskstats" ]] || warn ;;
esac
trap 'log_line "$(ts_ms) $RUN_ID STOP signal=TERM"; exit 0' TERM INT
TICK0=$(ts_ms)
while :; do
	case "$MODE" in
	pressure)
		for resource in memory io cpu; do
			ts=$(ts_ms)
			file=$PROC_ROOT/pressure/$resource
			if [[ -r "$file" ]]; then
				while IFS= read -r line || [[ -n "$line" ]]; do
					log_line "$ts $RUN_ID $resource $line"
				done <"$file"
			else
				log_line "$ts $RUN_ID $resource 없음"
			fi
		done
		;;
	procvmstat)
		ts=$(ts_ms)
		values=""
		if [[ -r "$PROC_ROOT/vmstat" ]]; then
			while read -r key value; do
				case "$key" in
				pgfault|pgmajfault|pgpgin|pgpgout|pswpin|pswpout|pgscan*|pgsteal*) values="$values $key=$value" ;;
				esac
			done <"$PROC_ROOT/vmstat"
		fi
		log_line "$ts $RUN_ID${values:- 없음}"
		;;
	conntrack)
		ts=$(ts_ms)
		count=없음 max=없음
		[[ ! -r "$PROC_ROOT/sys/net/netfilter/nf_conntrack_count" ]] || read -r count <"$PROC_ROOT/sys/net/netfilter/nf_conntrack_count" || true
		[[ ! -r "$PROC_ROOT/sys/net/netfilter/nf_conntrack_max" ]] || read -r max <"$PROC_ROOT/sys/net/netfilter/nf_conntrack_max" || true
		log_line "$ts $RUN_ID count=$count max=$max"
		;;
	diskstats)
		ts=$(ts_ms)
		if [[ -r "$PROC_ROOT/diskstats" ]]; then
			while read -r line || [[ -n "$line" ]]; do
				[[ -z "$line" ]] || log_line "$ts $RUN_ID $line"
			done <"$PROC_ROOT/diskstats"
		else
			log_line "$ts $RUN_ID 없음"
		fi
		;;
	esac
	sleep_to_next_tick
done
