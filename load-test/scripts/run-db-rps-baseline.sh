#!/usr/bin/env bash
set -uo pipefail
umask 077

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

date_hint="SESSION_DATE에 유효한 YYYYMMDD 날짜를 지정하세요. 예: export SESSION_DATE=20261118"
[[ "${SESSION_DATE:-}" =~ ^[0-9]{8}$ && "$SESSION_DATE" != 0000* ]] || fail "$date_hint"
iso_date="${SESSION_DATE:0:4}-${SESSION_DATE:4:2}-${SESSION_DATE:6:2}"
# GNU date(EC2)와 BSD date(Mac)에서 같은 날짜인지 확인한다.
checked_date=$(date -u -d "$iso_date" +%Y%m%d 2>/dev/null ||
    date -u -j -f '%Y-%m-%d' "$iso_date" +%Y%m%d 2>/dev/null) || fail "$date_hint"
[[ "$checked_date" == "$SESSION_DATE" ]] || fail "$date_hint"

for name in TARGET_RPS TOTAL RUN_ID BASE_URL TOKENS_FILE PRE_VUS; do
    [[ -n "${!name:-}" ]] || fail "$name 값을 지정하세요."
done
if [[ ! "$RUN_ID" =~ ^[0-9]{8}-[0-9]{6}-rps([1-9][0-9]*)-cap[1-9][0-9]*(-warmup)?$ ]]; then
    fail "RUN_ID는 prepare에서 출력한 실행 ID를 사용하세요."
fi
[[ "$TARGET_RPS" == "${BASH_REMATCH[1]}" ]] || fail "TARGET_RPS가 RUN_ID의 목표 RPS와 다릅니다."
[[ "$TOTAL" =~ ^[1-9][0-9]*$ ]] || fail "TOTAL은 양의 정수여야 합니다."
[[ "$TOKENS_FILE" = /* && -f "$TOKENS_FILE" && -r "$TOKENS_FILE" ]] ||
    fail "TOKENS_FILE은 읽을 수 있는 토큰 파일의 절대 경로여야 합니다."
detail="${DETAIL:-0}"
[[ "$detail" == 0 || "$detail" == 1 ]] || fail "DETAIL은 0 또는 1로 지정하세요."
command -v k6 >/dev/null || fail "k6를 PATH에서 찾을 수 없습니다."

load_test_dir=$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd) ||
    fail "load-test 디렉터리를 찾을 수 없습니다."
scenario="$load_test_dir/k6/db-rps-baseline.js"
[[ -f "$scenario" ]] || fail "k6 시나리오를 찾을 수 없습니다: $scenario"

RESULTS_ROOT="$load_test_dir/results/$SESSION_DATE"
run_dir="$RESULTS_ROOT/$RUN_ID"
mkdir -p -- "$RESULTS_ROOT" || fail "세션 결과 폴더 생성 실패: $RESULTS_ROOT"
if ! mkdir -- "$run_dir"; then
    [[ ! -e "$run_dir" && ! -L "$run_dir" ]] || fail "이미 존재하는 실행 결과: $run_dir"
    fail "실행 결과 폴더 생성 실패: $run_dir"
fi

k6_command=(env -u K6_HTTP_DEBUG k6 run --no-color --quiet=false --log-output=stderr --include-system-env-vars=false)
k6_command+=(-e RUNNER_FOOTER=1)
detail_file="$run_dir/k6-metrics.jsonl.gz"
if [[ "$detail" == 1 ]]; then
    k6_command+=(--out "json=$detail_file")
fi
for name in TARGET_RPS TOTAL RUN_ID BASE_URL TOKENS_FILE RESULTS_ROOT PRE_VUS MAX_VUS REQ_TIMEOUT_SECONDS; do
    if [[ -n "${!name:-}" ]]; then
        k6_command+=(-e "$name=${!name}")
    fi
done
k6_command+=("$scenario")

{
    printf '%q ' "${k6_command[@]}"
    printf '\n'
} > "$run_dir/k6-command.txt" || fail "실행 명령 기록 실패: $run_dir"
console_log="$run_dir/k6-console.log"
: > "$console_log" || fail "콘솔 로그 준비 실패: $console_log"

printf 'k6 결과 저장 위치: %s\n' "$run_dir"
"${k6_command[@]}" 2>&1 | tee -- "$console_log"
statuses=("${PIPESTATUS[@]}")
footer="$run_dir/.k6-summary.txt"
{
    if [[ "$detail" == 1 && -s "$detail_file" ]]; then
        printf '\n시각별 k6 지표: %s\n' "$detail_file"
    fi
    if [[ -s "$footer" ]]; then
        cat -- "$footer"
    elif (( statuses[0] != 0 )); then
        printf '\n실행 실패 (k6 종료 코드 %s). 먼저 콘솔 로그를 확인하세요: %s\n' "${statuses[0]}" "$console_log"
    fi
} | tee -a -- "$console_log"
footer_status=$?
if (( footer_status == 0 )) && [[ -f "$footer" ]]; then
    rm -- "$footer"
fi
if (( statuses[1] != 0 )); then
    printf '콘솔 로그 저장 실패: %s\n' "$console_log" >&2
fi
if (( statuses[0] != 0 )); then
    exit "${statuses[0]}"
fi
if (( statuses[1] != 0 )); then
    exit "${statuses[1]}"
fi
(( footer_status == 0 )) || fail "종료 요약을 콘솔 로그에 기록하지 못했습니다: $console_log"
[[ -s "$run_dir/k6-rps-summary.json" ]] || fail "k6 종료 요약 파일이 없습니다: $run_dir"
if [[ "$detail" == 1 && ! -s "$detail_file" ]]; then
    fail "시각별 k6 지표 파일이 없습니다: $run_dir"
fi
