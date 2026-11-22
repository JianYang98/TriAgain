#!/usr/bin/env bash
# RPS 가입 부하 테스트 실행 스크립트 (러너 EC2에서 실행)
# prepare가 출력한 "k6 부하 테스트 창" 블록이 이 스크립트를 부른다.
#
# 흐름: 1) 입력 검증 → 2) 결과 폴더 준비 → 3) k6 명령 구성 → 4) k6 실행 → 5) 결과 검증·종료 코드
# 필수 환경변수: SESSION_DATE TARGET_RPS REQUESTS RUN_ID BASE_URL TOKENS_FILE PRE_VUS
# 선택 환경변수: DETAIL(0|1, 기본 0) MAX_VUS REQ_TIMEOUT_SECONDS
# 결과 폴더: load-test/results/<SESSION_DATE>/<RUN_ID>/

# -u: 정의되지 않은 변수를 쓰면 중단. pipefail: 파이프 중 하나라도 실패하면 전체 실패.
# -e는 일부러 쓰지 않는다. k6가 실패해도 아래에서 안내를 남기고 종료 코드를 직접 정한다.
set -uo pipefail
# 결과 폴더·파일을 본인만 읽고 쓸 수 있게 만든다.
umask 077

# 메시지를 stderr에 출력하고 종료 코드 1로 끝낸다.
# `조건 || fail "메시지"` = 조건이 거짓이면 메시지를 출력하고 종료한다.
fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

# ===== 1) 입력 검증 =====

# SESSION_DATE: 8자리 숫자, 연도 0000은 거절
date_hint="SESSION_DATE에 유효한 YYYYMMDD 날짜를 지정하세요. 예: export SESSION_DATE=20261118"
[[ "${SESSION_DATE:-}" =~ ^[0-9]{8}$ && "$SESSION_DATE" != 0000* ]] || fail "$date_hint"
# ${변수:시작:길이}는 문자열 자르기다. 20261118 → 2026-11-18
iso_date="${SESSION_DATE:0:4}-${SESSION_DATE:4:2}-${SESSION_DATE:6:2}"
# GNU date(EC2)와 BSD date(Mac)에서 같은 날짜인지 확인한다.
# GNU date를 먼저 쓰고, 실패하면 BSD date를 쓴다.
# Mac date는 없는 날짜를 다른 날짜로 바꾸므로(20260230 → 20260302) 원래 값과 다시 비교한다.
checked_date=$(date -u -d "$iso_date" +%Y%m%d 2>/dev/null ||
    date -u -j -f '%Y-%m-%d' "$iso_date" +%Y%m%d 2>/dev/null) || fail "$date_hint"
[[ "$checked_date" == "$SESSION_DATE" ]] || fail "$date_hint"

# 필수 값 확인. ${!name}은 이름이 name에 담긴 변수의 값이다(name=RUN_ID → $RUN_ID).
for name in TARGET_RPS REQUESTS RUN_ID BASE_URL TOKENS_FILE PRE_VUS; do
    [[ -n "${!name:-}" ]] || fail "$name 값을 지정하세요."
done

# RUN_ID 예: 20261118-153000-rps150-cap10-warmup
# 정규식의 괄호 부분(rps 뒤 숫자)이 BASH_REMATCH[1]에 저장되어 바로 아래 TARGET_RPS 비교에 쓰인다.
# 이 비교가 TARGET_RPS의 숫자 형식 검사도 겸한다.
if [[ ! "$RUN_ID" =~ ^[0-9]{8}-[0-9]{6}-rps([1-9][0-9]*)-cap[1-9][0-9]*(-warmup)?$ ]]; then
    fail "RUN_ID는 prepare에서 출력한 실행 ID를 사용하세요."
fi
[[ "$TARGET_RPS" == "${BASH_REMATCH[1]}" ]] || fail "TARGET_RPS가 RUN_ID의 목표 RPS와 다릅니다."
[[ "$REQUESTS" =~ ^[1-9][0-9]*$ ]] || fail "REQUESTS는 양의 정수여야 합니다."

# 절대 경로(/로 시작) + 일반 파일 + 읽기 가능
[[ "$TOKENS_FILE" = /* && -f "$TOKENS_FILE" && -r "$TOKENS_FILE" ]] ||
    fail "TOKENS_FILE은 읽을 수 있는 토큰 파일의 절대 경로여야 합니다."

detail="${DETAIL:-0}"
[[ "$detail" == 0 || "$detail" == 1 ]] || fail "DETAIL은 0 또는 1로 지정하세요."
command -v k6 >/dev/null || fail "k6를 PATH에서 찾을 수 없습니다."

# ===== 2) 결과 폴더 준비 =====

# 이 스크립트가 있는 scripts/의 상위 폴더, 즉 load-test/의 절대 경로
load_test_dir=$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd) ||
    fail "load-test 디렉터리를 찾을 수 없습니다."
scenario="$load_test_dir/k6/rps-join-test.js"
[[ -f "$scenario" ]] || fail "k6 시나리오를 찾을 수 없습니다: $scenario"

# RESULTS_ROOT만 대문자인 이유: 3)에서 이 이름 그대로 k6에 넘긴다.
RESULTS_ROOT="$load_test_dir/results/$SESSION_DATE"
run_dir="$RESULTS_ROOT/$RUN_ID"
mkdir -p -- "$RESULTS_ROOT" || fail "세션 결과 폴더 생성 실패: $RESULTS_ROOT"
# 실행 폴더는 -p 없이 만든다. 같은 RUN_ID 폴더가 이미 있으면 실패하므로 이전 결과를 덮어쓰지 않는다.
# 실패 시: 폴더나 깨진 심볼릭 링크(-L)가 이미 있으면 "이미 존재", 그 밖의 이유면 "생성 실패"
if ! mkdir -- "$run_dir"; then
    [[ ! -e "$run_dir" && ! -L "$run_dir" ]] || fail "이미 존재하는 실행 결과: $run_dir"
    fail "실행 결과 폴더 생성 실패: $run_dir"
fi

# ===== 3) k6 명령 구성 =====

k6_command=(
    # 셸에 K6_HTTP_DEBUG가 남아 있어도 HTTP 디버그 출력이 켜지지 않게 지우고 k6를 실행한다.
    env -u K6_HTTP_DEBUG
    k6 run
    --no-color
    --quiet=false
    --log-output=stderr
    # 시나리오에는 아래 -e로 넘긴 값만 보인다(셸 환경변수는 안 보임).
    --include-system-env-vars=false
)
detail_file="$run_dir/k6-metrics.jsonl.gz"
if [[ "$detail" == 1 ]]; then
    k6_command+=(--out "json=$detail_file")
fi
# 값이 있는 변수만 -e 이름=값으로 넘긴다(MAX_VUS·REQ_TIMEOUT_SECONDS는 선택값).
# SESSION_DATE·DETAIL은 이 스크립트에서만 쓴다. 이 순서 그대로 k6-command.txt에 기록된다.
for name in TARGET_RPS REQUESTS RUN_ID BASE_URL TOKENS_FILE RESULTS_ROOT PRE_VUS MAX_VUS REQ_TIMEOUT_SECONDS; do
    if [[ -n "${!name:-}" ]]; then
        k6_command+=(-e "$name=${!name}")
    fi
done
k6_command+=("$scenario")

# ===== 4) k6 실행 =====

# 실행 명령 기록. %q는 셸에 그대로 붙여넣어 다시 실행할 수 있게 따옴표를 처리한다.
{
    printf '%q ' "${k6_command[@]}"
    printf '\n'
} > "$run_dir/k6-command.txt" || fail "실행 명령 기록 실패: $run_dir"
console_log="$run_dir/k6-console.log"
# `: > 파일`은 빈 파일을 만든다. k6 실행 전에 로그 파일을 쓸 수 있는지 확인한다.
: > "$console_log" || fail "콘솔 로그 준비 실패: $console_log"

printf 'k6 결과 저장 위치: %s\n' "$run_dir"
# 2>&1로 에러 출력까지 합쳐서, tee로 화면과 콘솔 로그 양쪽에 남긴다.
"${k6_command[@]}" 2>&1 | tee -- "$console_log"
# PIPESTATUS는 다음 명령이 실행되면 바뀌므로 바로 복사한다. [0]=k6, [1]=tee 종료 코드
statuses=("${PIPESTATUS[@]}")

# ===== 5) 결과 검증·종료 코드 =====

# 종료 요약은 k6(handleSummary)가 화면과 k6-summary.txt에 직접 쓴다. 이 파일이 없으면 요약 전에 k6가 멈춘 것이다.
summary_text="$run_dir/k6-summary.txt"
# 실행 후 안내를 화면과 콘솔 로그에 남긴다(tee -a = 이어 쓰기). -s는 "파일이 있고 비어 있지 않음".
{
    if [[ "$detail" == 1 && -s "$detail_file" ]]; then
        printf '\n시각별 k6 지표: %s\n' "$detail_file"
    fi
    if [[ ! -s "$summary_text" ]] && (( statuses[0] != 0 )); then
        printf '\n실행 실패 (k6 종료 코드 %s). 먼저 콘솔 로그를 확인하세요: %s\n' "${statuses[0]}" "$console_log"
    fi
} | tee -a -- "$console_log"
footer_status=$?

# 종료 코드 우선순위: k6 실패 → tee(콘솔 로그 저장) 실패 → 나머지 검사 실패(1)
# 로그 저장 실패는 k6가 실패했더라도 먼저 알린다.
if (( statuses[1] != 0 )); then
    printf '콘솔 로그 저장 실패: %s\n' "$console_log" >&2
fi
if (( statuses[0] != 0 )); then
    exit "${statuses[0]}"
fi
if (( statuses[1] != 0 )); then
    exit "${statuses[1]}"
fi
(( footer_status == 0 )) || fail "실행 후 안내를 콘솔 로그에 기록하지 못했습니다: $console_log"
[[ -s "$run_dir/k6-rps-summary.json" ]] || fail "k6 종료 요약 파일이 없습니다: $run_dir"
if [[ "$detail" == 1 && ! -s "$detail_file" ]]; then
    fail "시각별 k6 지표 파일이 없습니다: $run_dir"
fi
