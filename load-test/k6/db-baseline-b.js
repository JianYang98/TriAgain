// ============================================================
// DB baseline B 테스트 — 정원 1,000 크루 1곳에 서로 다른 사용자 TOTAL명이 TARGET_RPS로 한 번씩 가입 시도.
// 지시서: 루트 sdd/crew-join-db-baseline/instructions/C5-k6-scenario.md
// 요청 1건 = iteration 1회. 토큰은 exec.scenario.iterationInTest로 고르고 되감지 않는다.
// 원격 import 0건, k6 빌트인만. k6에서 /internal/gc를 부르지 않는다 (강제 GC는 test-plan §5 별도 단계).
//
// 실행 (사용자가 맥에서. TOKENS_FILE은 절대 경로. OUT_DIR 폴더는 미리 만든다 — 없으면 요약 파일 저장만
// 실패하고 k6는 exit 0으로 끝난다. --out json을 같은 폴더에 주면 폴더가 없을 때 요청 전에 실패한다):
// # 본 측정
// k6 run --out json=$OUT_DIR/k6.json \
//   -e BASE_URL=... -e CREW_ID=... -e TARGET_RPS=50 -e TOTAL=10000 -e WARMUP=0 \
//   -e TOKENS_FILE=... -e RUN_ID=... -e OUT_DIR=... \
//   load-test/k6/db-baseline-b.js
// # 워밍업: 같은 스크립트, 워밍업 크루, WARMUP=1 (요약은 $OUT_DIR/warmup-summary.json, 본 측정 파일을 덮지 않는다)
// #   예) -e CREW_ID=dbb-warm-01 -e TOTAL=1000 -e TARGET_RPS=50 (건수·RPS는 스모크에서 확정)
// #   --out json은 k6.json과 다른 경로로 준다 (예: --out json=$OUT_DIR/warmup-k6.json)
//
// 환경변수: BASE_URL CREW_ID TARGET_RPS TOKENS_FILE RUN_ID (필수)
//   TOTAL(10000) OUT_DIR(없으면 파일 미작성) REQ_TIMEOUT(30s, <정수>s) PRE_VUS(ceil(RPS*0.5))
//   MAX_VUS(max(200, RPS*5)) WARMUP(0|1, 기본 0)
// handleSummary가 받는 data는 k6 v2.3.0 기본(legacy) 형식이다 — --new-machine-readable-summary를 쓰지 않는다.
// ============================================================
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { loadTokens } from './lib/b-tokens.js';
import { COUNTER_BY_RESULT, RESULTS, record } from './lib/b-metrics.js';

// ===== 환경변수 (잘못되면 init에서 throw → 요청 0건) =====
function env(name, def) {
	const v = __ENV[name];
	if (v !== undefined && v !== '') return v;
	if (def === undefined) throw new Error(`필수 환경변수 누락: ${name}`);
	return def;
}
function posInt(name, def) {
	const v = env(name, def);
	if (!/^[1-9][0-9]*$/.test(v)) throw new Error(`${name}는 양의 정수여야 한다: ${v}`);
	return parseInt(v, 10);
}
function ident(name) {
	const v = env(name);
	if (!/^[A-Za-z0-9-]{1,36}$/.test(v)) throw new Error(`${name}는 [A-Za-z0-9-] 1~36자여야 한다`);
	return v;
}

const BASE_URL = env('BASE_URL').replace(/\/+$/, '');
const CREW_ID = ident('CREW_ID');
const RUN_ID = ident('RUN_ID');
const TARGET_RPS = posInt('TARGET_RPS');
const TOTAL = posInt('TOTAL', '10000');
const TOKENS_FILE = env('TOKENS_FILE');
const OUT_DIR = (__ENV.OUT_DIR || '').replace(/\/+$/, '');
const WARMUP = env('WARMUP', '0');
const REQ_TIMEOUT = env('REQ_TIMEOUT', '30s');
const PRE_VUS = posInt('PRE_VUS', String(Math.ceil(TARGET_RPS * 0.5)));
const MAX_VUS = posInt('MAX_VUS', String(Math.max(200, TARGET_RPS * 5)));

if (WARMUP !== '0' && WARMUP !== '1') throw new Error(`WARMUP은 0 또는 1: ${WARMUP}`);
if (TOTAL % TARGET_RPS !== 0) throw new Error(`TOTAL(${TOTAL}) % TARGET_RPS(${TARGET_RPS}) != 0 — duration이 정수 초가 아니다`);
if (!/^[1-9][0-9]*s$/.test(REQ_TIMEOUT)) throw new Error(`REQ_TIMEOUT은 <정수>s 형식: ${REQ_TIMEOUT}`);
if (!TOKENS_FILE.startsWith('/')) throw new Error('TOKENS_FILE은 절대 경로여야 한다');

const tokens = loadTokens(TOKENS_FILE, TOTAL, WARMUP === '1');

// duration = TOTAL/TARGET_RPS 초에서 반 주기(0.5/RPS 초)를 뺀다.
// k6 v2.3.0 constant-arrival-rate는 t=0부터 1/RPS 간격으로 시작하고, 정확히 t=duration에 걸린 한 건을
// 타이머와 종료가 경합해 가끔 더 시작한다 (실측: rate×duration +1이 16회 중 6회). 반 주기를 빼면 정확히 TOTAL건.
const DURATION_US = (TOTAL * 1e6) / TARGET_RPS - Math.floor(5e5 / TARGET_RPS);

// ===== 시나리오 =====
export const options = {
	scenarios: {
		join: {
			executor: 'constant-arrival-rate',
			rate: TARGET_RPS,
			timeUnit: '1s',
			duration: `${DURATION_US}us`,
			preAllocatedVUs: PRE_VUS,
			maxVUs: MAX_VUS,
			// 마지막 요청도 REQ_TIMEOUT까지 기다린 뒤 끊기도록 5초 여유
			gracefulStop: `${parseInt(REQ_TIMEOUT, 10) + 5}s`,
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
	// 영향 없는 threshold — 태그별 서브메트릭(join_duration{result:…})을 요약에 만들기 위한 것. 중단 조건 없음.
	thresholds: Object.fromEntries(RESULTS.map((r) => [`join_duration{result:${r}}`, ['p(95)>=0']])),
};

export default function () {
	const i = exec.scenario.iterationInTest;
	if (i >= tokens.length) throw new Error(`iteration ${i} >= 토큰 ${tokens.length} — 되감지 않는다`);
	const res = http.post(`${BASE_URL}/crews/${CREW_ID}/join`, null, {
		headers: { Authorization: `Bearer ${tokens[i]}` },
		tags: { name: 'join' },
		timeout: REQ_TIMEOUT,
	});
	const r = record(res);
	// 기대 결과는 201·CR002뿐 → checks의 fails = 예상 밖 결과 수 (http_req_failed는 쓰지 않는다)
	check(r, { 'expected 201 or CR002': (x) => x === '201' || x === 'cr002' });
}

// ===== 종료 요약 =====
export function handleSummary(data) {
	const m = data.metrics;
	const count = (k) => (m[k] ? m[k].values.count : 0);
	const counts = {};
	for (const r of RESULTS) counts[COUNTER_BY_RESULT[r]] = count(COUNTER_BY_RESULT[r]);
	const classifiedTotal = Object.values(counts).reduce((a, b) => a + b, 0);
	const iterations = count('iterations');
	const block = {
		run_id: RUN_ID,
		crew_id: CREW_ID,
		warmup: WARMUP === '1',
		target_rps: TARGET_RPS,
		total: TOTAL,
		counts,
		classified_total: classifiedTotal,
		iterations,
		classified_equals_iterations: classifiedTotal === iterations,
		iterations_equals_total: iterations === TOTAL,
	};

	const checks = m.checks ? m.checks.values : { passes: 0, fails: 0 };
	const pct = (r, p) => {
		const sm = m[`join_duration{result:${r}}`];
		return counts[COUNTER_BY_RESULT[r]] && sm ? `${sm.values[p].toFixed(1)}ms` : '-'; // 0건이면 표본 없음
	};
	let out = `\n=== DB baseline B  run=${RUN_ID} crew=${CREW_ID} warmup=${WARMUP} rps=${TARGET_RPS} total=${TOTAL}\n`;
	for (const r of RESULTS) {
		const name = COUNTER_BY_RESULT[r];
		out += `  ${name.padEnd(20)} ${String(counts[name]).padStart(6)}   p95=${pct(r, 'p(95)')} p99=${pct(r, 'p(99)')}\n`;
	}
	out += `  classified_total ${classifiedTotal} / iterations ${iterations} → ${block.classified_equals_iterations ? 'OK' : 'MISMATCH'}`;
	out += `   iterations/total ${iterations}/${TOTAL} → ${block.iterations_equals_total ? 'OK' : 'MISMATCH'}\n`;
	out += `  checks_failed ${checks.fails} (checks_passed ${checks.passes})   dropped_iterations ${count('dropped_iterations')}\n`;

	const result = { stdout: out };
	if (OUT_DIR) {
		const file = `${OUT_DIR}/${WARMUP === '1' ? 'warmup-summary.json' : 'k6-summary.json'}`;
		result[file] = JSON.stringify(Object.assign({}, data, { dbb: block }), null, 2);
		result.stdout += `  summary → ${file}\n`;
	}
	return result;
}
