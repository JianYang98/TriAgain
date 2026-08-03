//   아예 샘플코드  가져와서 그거 기반으로 만듬
//
// 50 VUs가 정원 기본 10명, --env MAX_MEMBERS=100으로 확대 가능 크루에 동시 참가 시도.
// SELECT FOR UPDATE 경합 + 정원 초과 처리 검증.
//
// 실행 전 반드시:
//   psql ... -f load-test/sql/07_rush_reset.sql
//
// k6 run --env BASE_URL=http://<SERVER> crew-rush-jian.js
// k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=100 --env RUSH_CREW_COUNT=1 crew-rush.js
// ============================================================
// ===== init 단계 (최상위) =====
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { tokens } from "./lib/config.js"; // 토큰만 lib에서
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/2.4.0/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";

const scenarioDDuration = new Trend("scenario_d_duration", true);
const joinSuccess = new Counter("join_success");
const joinFull = new Counter("join_full"); // 정원 초과
const joinConflict = new Counter("join_conflict"); // 재시도고갈
const joinError5xx = new Counter("join_5xx"); // 서버에러
const joinDropped = new Counter("join_dropped"); //  연결 실패 / 리셋
const joinOther = new Counter("join_other"); // 201/409/5xx/0 외 전부

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080"; // 36행: 기본값 추가
const RUSH_CREW_COUNT = parseInt(__ENV.RUSH_CREW_COUNT || "10"); // [신규] 추가

function authHeaders(vu) {
  // 가져옴 토큰 샤샤샥  (config.js 61~69행)
  const token = tokens[(vu - 1) % tokens.length];
  return {
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  };
}
const vus = parseInt(__ENV.TARGET_VUS || "50");
const maxMembers = parseInt(__ENV.MAX_MEMBERS || "10");
//const rushCrewId = parseInt(__ENV.RUSH_CREW_ID || "1");
const expSuccess = Math.min(vus, maxMembers);
const expFull = Math.max(0, vus - maxMembers);

// [가드] RUN_TAG ↔ TARGET_VUS 불일치 시 즉시 중단.
// TAG= 할당 줄이 셸에 안 들어가면 직전 태그로 raw가 덮어써지는 사고 방지(07-09 A5 vu50/vu200 오태그).
// init 단계 throw는 --out json 파일을 열기 전이라 기존 raw 파일은 보존된다(실험 검증).
if (__ENV.RUN_TAG && !__ENV.RUN_TAG.endsWith(`vu${vus}`)) {
  throw new Error(
    `RUN_TAG(${__ENV.RUN_TAG})가 vu${vus}로 끝나지 않음 — TAG 줄 누락 의심, 실행 중단`
  );
}

// [신규] pre-GC 게이트 — VU 램프 전 서버 강제 GC로 힙 초기조건 균일화.
// PRE_GC 토글: 기본 "on"(게이트 작동). PRE_GC=off 이면 GC 호출 없이 즉시 return
//   → 무게이트 arm(게이트 효과 A/B 측정용). 게이트 ON일 때 실패는 throw로 런 자체 차단
//   (미GC 런 구조적 차단 — RUN_TAG 가드와 같은 철학).
const GC_GATE_ON = (__ENV.PRE_GC || "on").toLowerCase() !== "off";
const GC_API_KEY = __ENV.GC_API_KEY || "loadtest-internal-key"; // application-loadtest.yml internal.api-key
export function setup() {
  if (!GC_GATE_ON) {
    console.log("[pre-GC] SKIPPED (PRE_GC=off) — 무게이트 arm"); // off arm 증빙(GC 호출·http 요청 0건)
    return;
  }
  const res = http.post(`${BASE_URL}/internal/gc`, null, {
    headers: { "X-Internal-Api-Key": GC_API_KEY },
    tags: { name: "pre-gc" }, // built-in http_req_* 오염 필터용
  });
  if (res.status !== 200) {
    throw new Error(`pre-GC 실패 (status=${res.status} body=${res.body}) — 런 중단`);
  }
  console.log(`[pre-GC] ${res.body}`); // 회수량·소요시간이 k6 stdout에 증빙으로 남음
  sleep(5); // GC 여진이 램프에 안 물리게 정착 대기
}

export const options = {
  scenarios: {
    rush: {
      executor: "per-vu-iterations",
      exec: "rushExec", // 실행할 함수 이름
      vus: vus, // 유저수
      iterations: 1, // 반복
      maxDuration: "30s", // 얼마동안
    },
  },
  thresholds: {
    join_success: [`count==${expSuccess}`], // 정원 정확
    join_5xx: ["count==0"],
    join_dropped: ["count==0"],
    // p95·conflict 는 박스 ⚠️로 (hard-fail 아님)
  },
};

// 코드 진입접
export function rushExec() {
  const params = authHeaders(__VU); // 토큰
  const rushCrewId = `loadtest-rush-crew-${(__ITER % RUSH_CREW_COUNT) + 1}`;

  const res = http.post(`${BASE_URL}/crews/${rushCrewId}/join`, null, params);
  scenarioDDuration.add(res.timings.duration);

  if (res.status === 201) {
    joinSuccess.add(1);
  } else if (res.status === 409) {
    const code = errorCode(res);
    if (code === "CR023") {
      joinConflict.add(1); // 재시도 고갈
    } else {
      joinFull.add(1); // 정원 초과
    }
  } else if (res.status >= 500) {
    joinError5xx.add(1); // 서버에러
  } else if (res.status === 0) {
    joinDropped.add(1); // 연결 실패 / 리셋
  } else {
    joinOther.add(1); //  201/409/5xx/0 외 전부
  }

  check(res, {
    "valid status (201/409)": (r) => r.status === 201 || r.status === 409,
    "no server error (5xx)": (r) => r.status < 500,
    "no conn reset (status 0)": (r) => r.status !== 0,
    "409 has expected code": (r) => {
      if (r.status !== 409) return true;
      return ["CR002", "CR023", "CR004"].includes(errorCode(r));
    },
  });
}

// 응답 바디 error.code 안전 추출 (status 0/빈 바디/파싱 실패 시 null)
function errorCode(res) {
  try {
    const b = res.json(); // json으로 받아서
    return b && b.error ? b.error.code : null;
  } catch (_) {
    return null;
  }
}

// 서머리: report.js 안 쓰고 직접 (html/json/stdout) + RUSH를 한눈에 보이는 블록으로
export function handleSummary(data) {
  const m = data.metrics;
  const c = (n) => (m[n] ? m[n].values.count : 0);

  const success = c("join_success");
  const full = c("join_full");
  const conflict = c("join_conflict");
  const err5xx = c("join_5xx");
  const dropped = c("join_dropped");
  const other = c("join_other");
  const p95 =
    (m["scenario_d_duration"] && m["scenario_d_duration"].values["p(95)"]) || 0;

  // expSuccess·expFull 는 모듈 상단에서 정의 (threshold와 공유)
  const dataOk = success === expSuccess && full + conflict === expFull;
  const verdict = dataOk ? "✅ PASS" : "❌ FAIL";

  const tag = __ENV.RUN_TAG || "(no-tag)";
  let box = `\n=========\n 🏁 RUSH  ${tag}\n`;
  box += `    정원 ${maxMembers}명 · 동시참가 ${vus} VU\n\n`;
  box += `  성공_success: ${success}\n`;
  box += `  정원초과_full: ${full}\n`;
  box += `  충돌_conflict: ${conflict}\n`;
  box += `  5xx_err5xx: ${err5xx}\n`;
  box += `  드롭_dropped_연결실패: ${dropped}\n`;
  box += `  기타_other: ${other}\n`;
  box += `  p95: ${p95.toFixed(1)} ms\n`;
  box += `  ${"─".repeat(26)}\n`;
  box += `  판정: ${verdict}  (정합성 ${dataOk ? "OK" : "✗"} · 충돌 ${conflict} · p95 ${p95.toFixed(1)}ms)\n`;

  const rawTag = __ENV.RUN_TAG || "";
  const scriptName = __ENV.K6_SCRIPT_NAME || "crew-rush-jian";
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
  const base = `results/raw/${scriptName}${rawTag ? "_" + rawTag : ""}_${timestamp}`;

  return {
    [`${base}.html`]: htmlReport(data),
    [`${base}.summary.json`]: JSON.stringify(data, null, 2),
    stdout:
      textSummary(data, { indent: "  ", enableColors: true }) + box + "\n",
  };
}
