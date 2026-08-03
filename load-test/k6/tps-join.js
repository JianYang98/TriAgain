// ============================================================
// 크루 참여 TPS 측정 — 락 전략 3종 × 경합 2케이스 (지시서 v3)
// open model(arrival-rate)로 선착순 참여(쓰기) 경로의 TPS 한계 측정.
//
// 참고 원본: crew-rush-jian.js (인증·errorCode·카운터·handleSummary 관례 승계).
// 기존 파일(crew-rush-jian.js, lib/*)은 수정하지 않는다 — 본 파일은 신규.
//
// 실행 예 — 본측정(램핑 50→500 2m + 500 유지 1m):
//   k6 run --env BASE_URL=http://<HOST>:8080 --env RUN_TAG=PESSIMISTIC_A_r500 \
//     --env CASE=A --env MODE=ramp --env RATE=500 k6/tps-join.js
// 실행 예 — 스모크(고정 10 TPS 30s):
//   k6 run --env BASE_URL=http://<HOST>:8080 --env RUN_TAG=CONDITIONAL_A_r10 \
//     --env CASE=A --env MODE=const --env RATE=10 --env CONST_DURATION=30s \
//     --env PRE_ALLOC_VUS=10 --env MAX_VUS=30 k6/tps-join.js
//
// 선행: sql/10_tps_crews.sql 시드 + 매 런 직전 sql/10_tps_reset.sql
// ============================================================

// ===== init 단계 (최상위) =====
import http from "k6/http";
import { sleep } from "k6";
import { Counter, Gauge } from "k6/metrics";
import exec from "k6/execution";
import { tokens, authHeadersByIndex, BASE_URL } from "./lib/config.js"; // 기존 lib 재사용(무수정)
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/2.4.0/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";

// [409 정상화] 정원마감(CR002)/중복(CR004)/충돌(CR023) 409는 비즈니스 정상 응답이므로
// http_req_failed 집계에서 제외한다. 안 하면 CASE B에서 failed가 ~90%로 찍혀 threshold 무의미.
// ⚠️ 지시서 §4-3의 `options.responseCallback`은 k6에 존재하지 않는 키(오문법) — 정본은
//    init 컨텍스트의 전역 콜백 등록이다(k6 v1.6 공식문서 set-response-callback 확인).
http.setResponseCallback(http.expectedStatuses(201, 409));

// ===== 커스텀 카운터 (지시서 §4-3 표 그대로) =====
const joinSuccess = new Counter("join_success"); //           201
const joinRejectedFull = new Counter("join_rejected_full"); //  409 + CR002 정원 마감
const joinRejectedDup = new Counter("join_rejected_dup"); //    409 + CR004 중복 — 구조적으로 0이어야 함
const joinConflict = new Counter("join_conflict"); //           409 + CR023 낙관락 재시도 소진
const joinError = new Counter("join_error"); //                 >= 500
const joinDropped = new Counter("join_dropped"); //             status 0 (연결 실패/리셋 — HTTP 레이어)
const joinOther = new Counter("join_other"); //                 위 어디에도 안 걸리는 전부
// [신규 가드] crewIdx가 시드 수를 넘으면 요청을 안 보내고 여기 집계(404 오염 방지).
// 분배 수식(§4-2)은 그대로 두고, 시드 부족 상황만 자동 검출한다.
const bucketOverflow = new Counter("join_bucket_overflow");
// setup 종료~시나리오 종료 실측 벽시계(ms) — 성공 TPS의 분모.
// testRunDurationMs는 setup(pre-GC + sleep 5s)을 포함해 TPS를 ~3% 과소평가하므로 별도 계측.
const measurementWindow = new Gauge("measurement_window_ms");

// ===== ENV 파라미터 =====
const MODE = (__ENV.MODE || "ramp").toLowerCase(); // ramp | const
const CASE = __ENV.CASE === "B" ? "B" : "A";
const W = parseInt(__ENV.W || "20"); //  동시 활성 크루 수(윈도우)
const K = CASE === "B" ? 100 : 12; //    크루당 시도 수 — §4-2 고정값, 임의 변경 금지
const TOTAL_USERS = parseInt(__ENV.TOTAL_USERS || "3000");
// 시드 수와 반드시 일치: 10_tps_crews.sql 기본 A=6000 / B=800
const TOTAL_CREWS = parseInt(__ENV.TOTAL_CREWS || (CASE === "B" ? "800" : "6000"));

const RATE = parseInt(__ENV.RATE || "500");
const START_RATE = parseInt(__ENV.START_RATE || "50");
const RAMP_DURATION = __ENV.RAMP_DURATION || "2m";
const HOLD_DURATION = __ENV.HOLD_DURATION || "1m";
const CONST_DURATION = __ENV.CONST_DURATION || "30s";

// ===== init 가드 3종 — init throw는 raw 파일 열기 전이라 기존 raw 보존(crew-rush-jian.js 검증 방식) =====

// [불변식] W×K < TOTAL_USERS — 버킷 내 유저 중복 방지(§4-2). A: 20×12=240, B: 20×100=2000 < 3000.
if (W * K >= TOTAL_USERS) {
  throw new Error(
    `불변식 위반: W(${W})×K(${K})=${W * K} >= TOTAL_USERS(${TOTAL_USERS}) — join_rejected_dup 오염 발생, 실행 중단`
  );
}

// [토큰 수량] 부족하면 뒷구간 토큰이 순환 재사용돼 같은 버킷 내 중복 가입(CR004) 발생 → 즉시 중단
if (tokens.length < TOTAL_USERS) {
  throw new Error(
    `tokens.csv 부족: ${tokens.length}개 < TOTAL_USERS(${TOTAL_USERS}) — scripts/generate-tokens.sh <URL> ${TOTAL_USERS} 재실행 필요`
  );
}

// [RUN_TAG 재설계] {전략}_{A|B}_r{rate} 형식 강제 + CASE·RATE 교차 일치 검사.
// 기존 vu{N} 가드는 arrival-rate에선 항상 throw라 재사용 불가(지시서 §4-3).
// 태그 누락/불일치 시 직전 런 raw를 덮어쓰는 사고 방지(07-09 오태그 전례).
const TAG_RE = /^(PESSIMISTIC|OPTIMISTIC|CONDITIONAL)_(A|B)_r(\d+)$/;
const tagMatch = (__ENV.RUN_TAG || "").match(TAG_RE);
if (!tagMatch) {
  throw new Error(
    `RUN_TAG 형식 오류(${__ENV.RUN_TAG}) — {전략}_{A|B}_r{rate} 필요 (예: PESSIMISTIC_A_r500), 실행 중단`
  );
}
if (tagMatch[2] !== CASE) {
  throw new Error(`RUN_TAG 케이스(${tagMatch[2]}) ≠ CASE(${CASE}) — 라벨 불일치, 실행 중단`);
}
if (parseInt(tagMatch[3]) !== RATE) {
  throw new Error(`RUN_TAG rate(${tagMatch[3]}) ≠ RATE(${RATE}) — 라벨 불일치, 실행 중단`);
}
// ⚠️ RUN_TAG는 사람이 넣는 값 — 전략의 증빙이 될 수 없다.
//    라벨 정본은 JVM 부팅시각(actuator process_start_time_seconds) 기록이다(실행가이드 §2.3).

// [pre-GC 게이트] crew-rush-jian.js에서 그대로 승계 — 6런 힙 초기조건 균일화.
// PRE_GC 기본 "on". off이면 GC 호출 없이 return(무게이트 arm). 실패는 throw로 런 차단.
const GC_GATE_ON = (__ENV.PRE_GC || "on").toLowerCase() !== "off";
const GC_API_KEY = __ENV.GC_API_KEY || "loadtest-internal-key"; // application-loadtest.yml internal.api-key
export function setup() {
  if (!GC_GATE_ON) {
    console.log("[pre-GC] SKIPPED (PRE_GC=off) — 무게이트 arm");
    return { setupEndMs: Date.now() };
  }
  const res = http.post(`${BASE_URL}/internal/gc`, null, {
    headers: { "X-Internal-Api-Key": GC_API_KEY },
    tags: { name: "pre-gc" }, // built-in http_req_* 오염 필터용 (join 스코프 threshold와 분리)
    responseCallback: http.expectedStatuses(200), // 전역 콜백(201,409)에 200이 없어 failed로 새는 것 방지
  });
  if (res.status !== 200) {
    throw new Error(`pre-GC 실패 (status=${res.status} body=${res.body}) — 런 중단`);
  }
  console.log(`[pre-GC] ${res.body}`); // 회수량·소요시간 stdout 증빙
  sleep(5); // GC 여진이 램프에 안 물리게 정착 대기
  return { setupEndMs: Date.now() };
}

export function teardown(data) {
  // 시나리오 실측 벽시계 — setup(pre-GC+대기) 제외한 순수 측정 구간. 성공 TPS 분모의 정본.
  if (data && data.setupEndMs) {
    measurementWindow.add(Date.now() - data.setupEndMs);
  }
}

// [VU 산정 — 리틀의 법칙] 필요 VU ≈ 목표 rate × 예상 응답시간.
// 500 TPS × 0.5s(보수 가정 — 실측치 아님. "VU 250 breaking point"류 미검증 수치 인용 금지) = 250
// → 여유 2배로 maxVUs 500. preAllocatedVUs는 산정치 250 선할당(런 중 VU 콜드스타트 지연 회피).
const PRE_ALLOC_VUS = parseInt(__ENV.PRE_ALLOC_VUS || "250");
const MAX_VUS = parseInt(__ENV.MAX_VUS || "500");

const scenarios = {};
if (MODE === "const") {
  // 고정 rate 검증·스모크용
  scenarios.tps_join = {
    executor: "constant-arrival-rate",
    exec: "joinExec",
    rate: RATE,
    timeUnit: "1s",
    duration: CONST_DURATION,
    preAllocatedVUs: PRE_ALLOC_VUS,
    maxVUs: MAX_VUS,
  };
} else {
  // 한계 탐색용 기본 프로파일: START_RATE→RATE 램핑 RAMP_DURATION + RATE 유지 HOLD_DURATION
  scenarios.tps_join = {
    executor: "ramping-arrival-rate",
    exec: "joinExec",
    startRate: START_RATE,
    timeUnit: "1s",
    preAllocatedVUs: PRE_ALLOC_VUS,
    maxVUs: MAX_VUS,
    stages: [
      { target: RATE, duration: RAMP_DURATION },
      { target: RATE, duration: HOLD_DURATION },
    ],
  };
}

export const options = {
  scenarios,
  // k6 기본 summaryTrendStats에는 p(99)가 없다(공식 options reference 확인) —
  // §9 비교표의 p99 컬럼을 위해 명시. 빠뜨리면 summary.json에서 p99가 통째로 빈다.
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  thresholds: {
    // join 요청만 스코프 — setup의 pre-gc 표본이 p95/p99에 섞이는 것 차단(스모크 300건에선 유의미)
    "http_req_duration{name:join}": [`p(95)<${parseInt(__ENV.P95_MS || "500")}`], // 조정 가능 상수
    http_req_failed: ["rate<0.01"],
    join_rejected_dup: ["count==0"], // 버킷 불변식(W×K<유저수) 위반 자동 검출
    join_error: ["count==0"],
    join_dropped: ["count==0"],
    join_bucket_overflow: ["count==0"], // 시드 수 부족(RATE·duration 확대 시) 자동 검출
  },
};

// ===== 코드 진입점 =====
export function joinExec() {
  // [버킷(윈도우) 분산 — §4-2 핵심 설계, 수식 임의 변경 금지]
  // 활성 윈도우 W개에 트래픽 집중 → 선착순 마감 경합 보존, 윈도우가 넘어가며 성공 쓰기 지속.
  // iterationInTest: 로컬 단일 k6 프로세스에서 시나리오 전체 유일·0-base 순번(공식문서 확인).
  const iter = exec.scenario.iterationInTest;
  const bucket = Math.floor(iter / (W * K));
  const crewIdx = bucket * W + Math.floor(Math.random() * W);
  const userIdx = iter % TOTAL_USERS;

  if (crewIdx + 1 > TOTAL_CREWS) {
    bucketOverflow.add(1); // 시드 범위 초과 — 존재하지 않는 크루로 404 쏘는 대신 스킵 + threshold 검출
    return;
  }

  const crewId = `loadtest-tps-crew-${crewIdx + 1}`; // 시드 1-base(loadtest-tps-crew-1..N)와 정합
  const params = authHeadersByIndex(userIdx); // lib/config.js 기존 헬퍼 — {headers:{Authorization,...}}
  params.tags = { name: "join" };

  const res = http.post(`${BASE_URL}/crews/${crewId}/join`, null, params);

  if (res.status === 201) {
    joinSuccess.add(1);
  } else if (res.status === 409) {
    const code = errorCode(res);
    if (code === "CR002") {
      joinRejectedFull.add(1); // 정원 마감
    } else if (code === "CR004") {
      joinRejectedDup.add(1); // 중복 — 0이어야 정상
    } else if (code === "CR023") {
      joinConflict.add(1); // 낙관락 재시도 소진
    } else {
      joinOther.add(1);
    }
  } else if (res.status === 0) {
    joinDropped.add(1); // 연결 실패/리셋 — k6 스케줄 실패(dropped_iterations)와 다른 레이어임에 주의
  } else if (res.status >= 500) {
    joinError.add(1);
  } else {
    joinOther.add(1);
  }
}

// 응답 바디 error.code 안전 추출 (status 0/빈 바디/파싱 실패 시 null) — crew-rush-jian.js 승계
function errorCode(res) {
  try {
    const b = res.json();
    return b && b.error ? b.error.code : null;
  } catch (_) {
    return null;
  }
}

// 서머리: html/json/stdout — crew-rush-jian.js 골격 승계, TPS 분리 보고(§4-3) 추가
export function handleSummary(data) {
  const m = data.metrics;
  const c = (n) => (m[n] ? m[n].values.count : 0);

  const success = c("join_success");
  const full = c("join_rejected_full");
  const dup = c("join_rejected_dup");
  const conflict = c("join_conflict");
  const error5xx = c("join_error");
  const dropped = c("join_dropped");
  const other = c("join_other");
  const overflow = c("join_bucket_overflow");
  const droppedIters = c("dropped_iterations"); // arrival-rate 스케줄 실패(가용 VU 부족) — 한계 도달 신호
  const iters = c("iterations");

  // 분모: teardown이 기록한 시나리오 실측 벽시계(정본). 없으면 testRunDurationMs(setup 포함) 폴백.
  const windowMs =
    m["measurement_window_ms"] && m["measurement_window_ms"].values.value
      ? m["measurement_window_ms"].values.value
      : 0;
  const rawMs = data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : 0;
  const durMs = windowMs > 0 ? windowMs : rawMs;
  const durSec = durMs / 1000;
  const durSrc = windowMs > 0 ? "measurement_window(순수 시나리오)" : "testRunDurationMs(setup 포함 — 폴백)";

  // "성공 쓰기 TPS"와 "전체 처리 TPS" 분리(§4-3 보고 구분). 전체 처리 = 서버가 응답한 요청(dropped 제외).
  const processed = success + full + dup + conflict + error5xx + other;
  const successTps = durSec > 0 ? success / durSec : 0;
  const totalTps = durSec > 0 ? processed / durSec : 0;
  const achievedRate = durSec > 0 ? iters / durSec : 0;

  const joinDur = m["http_req_duration{name:join}"];
  const p95 = (joinDur && joinDur.values["p(95)"]) || 0;
  const p99 = (joinDur && joinDur.values["p(99)"]) || 0;

  const tag = __ENV.RUN_TAG || "(no-tag)";
  let box = `\n=========\n 🏁 TPS-JOIN  ${tag}  (라벨 참고용 — 전략 증빙 정본은 JVM 부팅시각)\n`;
  box += `    MODE=${MODE} CASE=${CASE} K=${K} W=${W} 목표rate=${RATE}/s\n\n`;
  box += `  성공_success: ${success}\n`;
  box += `  정원마감_rejected_full: ${full}\n`;
  box += `  중복_rejected_dup: ${dup}  ${dup === 0 ? "(불변식 OK)" : "🚨 버킷 불변식 위반 — 즉시 조사"}\n`;
  box += `  낙관락소진_conflict: ${conflict}\n`;
  box += `  5xx_error: ${error5xx}\n`;
  box += `  드롭_dropped(status=0): ${dropped}\n`;
  box += `  기타_other: ${other}\n`;
  box += `  버킷초과_overflow(요청 미발사): ${overflow}\n`;
  box += `  dropped_iterations(k6 스케줄 실패=VU 부족): ${droppedIters}  ${droppedIters > 0 ? "⚠️ rate 미달성 — 한계 도달 신호" : ""}\n`;
  box += `  ${"─".repeat(34)}\n`;
  box += `  달성 rate: ${achievedRate.toFixed(1)}/s (iterations ${iters} ÷ ${durSec.toFixed(1)}s)\n`;
  box += `  성공 쓰기 TPS: ${successTps.toFixed(2)}  ← 실측 success ÷ 실측 소요시간 ("크루당 10 고정" 가정 금지)\n`;
  box += `  전체 처리 TPS: ${totalTps.toFixed(2)}  (409 포함·dropped 제외 — CASE B에선 성공 TPS와 분리 해석)\n`;
  box += `  p95(join): ${p95.toFixed(1)} ms / p99(join): ${p99.toFixed(1)} ms\n`;
  box += `  분모 출처: ${durSrc} ${durSec.toFixed(1)}s\n`;

  const rawTag = __ENV.RUN_TAG || "";
  const scriptName = __ENV.K6_SCRIPT_NAME || "tps-join";
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
  const base = `results/raw/${scriptName}${rawTag ? "_" + rawTag : ""}_${timestamp}`;

  return {
    [`${base}.html`]: htmlReport(data),
    [`${base}.summary.json`]: JSON.stringify(data, null, 2),
    stdout: textSummary(data, { indent: "  ", enableColors: true }) + box + "\n",
  };
}
