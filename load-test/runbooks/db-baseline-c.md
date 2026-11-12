# DB Only C — 1,000 RPS로 10초간 가입 시도

[TIER 판정] Tier: 1 | 근거: 프로덕션 코드 무변경, 별도 부하테스트 스크립트·설명 추가 | 판정자: orchestrator | 2026-10-28

## 목적과 전제

정원 1,000명의 새 크루에 서로 다른 사용자 10,000명이 1회씩 가입을 시도한다.
요청 시작을 약 1ms 간격으로 10초 동안 분산한다. 1만 명의 동시 클릭을 재현하는 구성은 아니다.
5~10초 매진은 보장할 동작이 아니라 관측 대상이다. 기존 준비 SQL처럼 크루장 1명으로 시작하면
성공 기대치는 999건이다. 토큰 문자열 중복 검사는 같은 사용자의 서로 다른 토큰까지 판별하지 못하므로
사용자별 1개씩 발급했음을 별도로 확인한다.

B 파일·공통 라이브러리·기존 DB/RD 실행기는 보존한다. `run-db.py`는 B를 실행하고
50/200 RPS만 허용하므로 C 실행에 사용하지 않는다.
실제 계측을 포함한 실험에는 C용 `run-db-c.py`와 `finish-db-c.py`를 사용한다.
전체 순서·설정 파일·타임라인은 [C 테스트 계획서](2026-10-28-111519-db-only-c-test-plan-27056.md)를 따른다.
아래 직접 k6 명령은 설정 설명용이며, 계측 종료까지 자동 처리하는 실행기를 대신하지 않는다.
기존 [재현 가이드](../DBB-REPRODUCTION-GUIDE.md)의 격리 테스트 대상 확인, 새 크루 준비,
별도 크루 워밍업, 계측 시작/종료, 최종 SQL 검증 절차는 여전히 필요하다.
직접 실행은 수집기를 자동 시작/종료하지 않는다. 기존 runner의 자동 계측 종료를 대체하지 않는다.

## 설정

| 환경변수 | 기본값 | 의미 |
|---|---|---|
| `TARGET_RPS` | 1000 | 초당 시작 목표 |
| `TOTAL` | 10000 | 총 시작 목표, RPS로 나누어떨어져야 함 |
| `PRE_VUS` | 10000 | 미리 준비할 실행자 |
| `MAX_VUS` | PRE_VUS와 동일 | 최대 실행자, 기본은 실행 중 증설 없음 |
| `REQ_TIMEOUT_SECONDS` | 60 | 요청 하나의 응답 대기 제한(정수 초) |
| `WARMUP` | 0 | 1이면 토큰 파일에서 앞 TOTAL개 사용, 별도 요약 파일 |
| `BASE_URL`, `CREW_ID`, `RUN_ID` | 필수 | 격리 테스트 대상·새 실행 식별자 |
| `TOKENS_FILE`, `OUT_DIR` | 필수 | 비공개 토큰 파일·새 결과 폴더의 절대 경로 |

`TOTAL / TARGET_RPS`가 요청 투입 시간이다. B의 종료 경계 보정을 유지하여 기본 실행은
9.9995초로 설정한다. 실제 1만 건 달성은 별도로 검사한다.
요청별 대기 60초, 투입 종료 후 종료 여유 65초이므로 마지막 요청은 약 70초까지 대기할 수 있고
시나리오의 종료 경계는 약 75초다. 종료 여유는 요청별 대기 시간에 더해지는 시간이 아니다.

VU는 10000/10000으로 설정한다. 모든 요청이 대기하는 경우에도 실행자가 부족하지 않도록
시작 전에 1만 개를 준비하고, 실행 중에는 추가로 만들지 않는다.
이 값은 1만 건을 동시에 발사한다는 뜻이 아니다. 요청 시작 속도는 TARGET_RPS가 정한다.
M4·16GB에서 이 스크립트의 VU 1만 개를 감당할 수 있는지는 아직 검증하지 않았다.
실제 측정 전 맥의 CPU·메모리 압박·스왑과 연결 제한을 확인한다. 준비 단계에서부터
메모리가 부족하면 본 측정을 진행하지 않고 부하 발생 환경을 다시 정한다.
VU 개수가 충분해도 맥이나 네트워크 병목으로 요청이 누락될 수 있으므로 실제 건수와 dropped도 확인한다.
다시 실행할 때는 새 크루·새 RUN_ID·새 결과 폴더를 사용한다.

## 수동 실행 형식과 ulimit 복원

저장소 루트에서 실행한다. BASE_URL/CREW_ID/RUN_ID/TOKENS_FILE/OUT_DIR은 대상 확인 후
별도로 설정한다. 토큰 값 자체를 명령·로그에 넣지 않는다. OUT_DIR은 아직 없는 새 절대 경로여야 한다.
아래는 준비·워밍업·계측 READY 이후 본 측정에 사용하는 형식이다.
기존 실행 결과가 있는 폴더에서 재실행하지 않는다.

```bash
(
  : "${BASE_URL:?테스트 URL 필요}" "${CREW_ID:?새 크루 필요}" "${RUN_ID:?새 실행 ID 필요}"
  : "${TOKENS_FILE:?비공개 토큰 파일 절대경로 필요}" "${OUT_DIR:?새 결과 폴더 절대경로 필요}"
  K6_ORIGINAL_NOFILE=$(ulimit -S -n)
  trap 'ulimit -S -n "$K6_ORIGINAL_NOFILE"' EXIT
  if [ "$K6_ORIGINAL_NOFILE" != unlimited ] && [ "$K6_ORIGINAL_NOFILE" -lt 16384 ]; then
    ulimit -S -n 16384 || exit 1
  fi
  umask 077
  mkdir -p "$(dirname "$OUT_DIR")" || exit 1
  mkdir "$OUT_DIR" || exit 1
  k6 run --out "json=$OUT_DIR/k6-c.json" \
    -e BASE_URL="$BASE_URL" -e CREW_ID="$CREW_ID" -e RUN_ID="$RUN_ID" \
    -e TOKENS_FILE="$TOKENS_FILE" -e OUT_DIR="$OUT_DIR" \
    -e TARGET_RPS=1000 -e TOTAL=10000 \
    -e PRE_VUS=10000 -e MAX_VUS=10000 -e REQ_TIMEOUT_SECONDS=60 \
    load-test/k6/db-baseline-c.js
)
ulimit -S -n
```

괄호 안의 명령은 별도 셸에서 실행한다. 종료할 때 파일 디스크립터 제한을 원래 값으로 되돌린다.
기존 터미널의 제한은 바뀌지 않는다.

동시 연결이 1만 개까지 늘어날 가능성을 고려해 파일 디스크립터 제한은 16384로 잡는다.
이 값은 이번 실행을 위한 여유값이며, 충분한지는 실제 열린 파일·소켓 수로 확인한다.
제한을 16384로 올리지 못하면 테스트를 시작하지 말고, `ulimit -H -n`으로 허용 상한을 확인한다.
기존 제한이 이미 16384 이상이거나 무제한이면 값을 낮추지 않고 그대로 사용한다.
시스템의 영구 설정과 허용 상한은 변경하지 않는다.

워밍업에는 본 측정과 다른 크루, RUN_ID, 결과 폴더를 사용한다.
`WARMUP=1`을 지정하고, 워밍업에 사용할 총 요청 수와 초당 요청 수를 따로 정한다.
워밍업의 PRE_VUS와 MAX_VUS도 별도로 지정해 본 측정용 1만 VU 기본값이 적용되지 않도록 한다.

## 결과 확인 방법

- 가입 성공인 `201`과 정원 초과인 `409/CR002`를 정상 결과로 센다.
  다른 종류의 `409` 응답은 오류로 센다.
- 보낸 HTTP 요청 수와 완료한 실행 수가 모두 `TOTAL`과 같아야 한다.
  시작하지 못한 요청과 예상하지 않은 응답도 없어야 한다.
  이 조건을 충족하지 못하면 k6가 실패를 나타내는 종료 코드를 반환한다.
- p95 500ms는 아직 정한 성능 목표가 아니므로 통과 조건에 넣지 않는다.
- `http_req_failed`는 정원 초과도 실패로 세기 때문에 이번 테스트의 통과 조건으로 사용하지 않는다.
- `responses_during_arrival`은 요청 투입 기간 안에 받은 HTTP 응답 수다.
  `responses_after_arrival`은 그 기간이 끝난 뒤에 받은 HTTP 응답 수다.
  기본 설정에서는 테스트 시작 후 10초를 기준으로 나눈다.
- 연결 실패처럼 HTTP 응답을 받지 못한 경우는 위 두 항목에 포함하지 않고
  `join_transport_err`로 센다. 이 중 요청 대기 시간 초과는 `timeouts`에도 기록한다.
- `first_full_response_at_ms`는 첫 정원 초과 응답을 받은 시점이다.
  `last_success_response_at_ms`는 마지막 가입 성공 응답을 받은 시점이다.
  둘 다 테스트 시작 후 지난 시간을 밀리초로 나타낸다.
  응답을 받은 시점이므로 DB에서 정원이 찬 정확한 시점과는 차이가 있을 수 있다.
  해당 응답을 받지 못했다면 값은 `null`이다.
- p95와 p99에는 10초가 지난 뒤에 돌아온 응답도 포함한다. 응답 종류별로 나누어 표시한다.
- k6가 응답 대기를 끝낸 뒤에도 서버 처리는 계속될 수 있다.
  테스트 후 DB에서 최종 인원이 1,000명인지, 중복 가입이 없는지 따로 확인한다.
- `load_and_response_checks_passed`는 요청 수와 응답 종류를 검사한 결과다.
  이 값이 참이어도 DB 검증이나 성능 목표 검증까지 끝났다는 뜻은 아니다.
- 본 측정 요약은 `k6-c-summary.json`, 워밍업 요약은 `warmup-c-summary.json`에 저장한다.
  k6 v2.3.0의 기존 요약 형식을 사용하므로 `--new-machine-readable-summary`는 지정하지 않는다.
  실행 전에는 결과 폴더에 파일을 쓸 수 있는지 확인하고, 실행 후에는 요약이 저장됐는지 확인한다.

## 참조한 실제 파일

- `load-test/k6/db-baseline-b.js`
- `load-test/k6/db-baseline-c.js`
- `load-test/k6/lib/b-tokens.js`
- `load-test/k6/lib/b-metrics.js`
- `load-test/scripts/run-db.py`
- `load-test/scripts/run-db-c.py`
- `load-test/scripts/finish-db-c.py`
- `load-test/DBB-REPRODUCTION-GUIDE.md`
- `docs/spec/api-spec/crew.md`
- `docs/log/debugging-log.md`
