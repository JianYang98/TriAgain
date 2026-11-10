// DB Only C 단계 시연본(-d): db-baseline-c.js의 복제. 서로 다른 사용자 10,000명이 TARGET_RPS로 가입 시도한다.
// 차이는 ALLOW_FRACTIONAL_ARRIVAL=1일 때 10,000이 RPS로 나누어떨어지지 않아도(300·600) 허용하는 것뿐이다. 기본 0이면 원본과 같다.
// 실행·준비·ulimit 복원: ../runbooks/db-baseline-c.md
// B의 토큰 검증·응답 분류를 재사용한다. 이 파일은 서버 준비/초기화/GC를 하지 않는다.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';
import { loadTokens } from './lib/b-tokens.js';
import { COUNTER_BY_RESULT, RESULTS, record } from './lib/b-metrics.js';

// 1. 설정: k6 run -e NAME=value로 기본값을 덮어쓴다.
function readEnv(name, fallback) {
	const value = __ENV[name];
	if (value !== undefined && value !== '') return value;
	if (fallback !== undefined) return String(fallback);
	throw new Error(`필수 환경변수 누락: ${name}`);
}

function positiveInteger(name, fallback) {
	const value = readEnv(name, fallback);
	const number = Number(value);
	if (!/^[1-9][0-9]*$/.test(value) || !Number.isSafeInteger(number)) {
		throw new Error(`${name}는 안전한 범위의 양의 정수여야 한다`);
	}
	return number;
}

function identifier(name) {
	const value = readEnv(name);
	if (!/^[A-Za-z0-9-]{1,36}$/.test(value)) {
		throw new Error(`${name}는 [A-Za-z0-9-] 1~36자여야 한다`);
	}
	return value;
}

const BASE_URL = readEnv('BASE_URL').replace(/\/+$/, '');
const CREW_ID = identifier('CREW_ID');
const RUN_ID = identifier('RUN_ID');
const TARGET_RPS = positiveInteger('TARGET_RPS', 1000);
const TOTAL = positiveInteger('TOTAL', 10000);
const PRE_VUS = positiveInteger('PRE_VUS', 10000);
const MAX_VUS = positiveInteger('MAX_VUS', PRE_VUS);
const REQ_TIMEOUT_SECONDS = positiveInteger('REQ_TIMEOUT_SECONDS', 60);
const OUT_DIR = readEnv('OUT_DIR').replace(/\/+$/, '');
const TOKENS_FILE = readEnv('TOKENS_FILE');
const WARMUP = readEnv('WARMUP', 0);
// 1이면 TOTAL이 TARGET_RPS로 나누어떨어지지 않아도 허용한다 (예: 10,000건 ÷ 300 RPS = 33.333초). 기본 0.
const FRACTIONAL_OK = readEnv('ALLOW_FRACTIONAL_ARRIVAL', 0);

if (!/^https?:\/\/[^\s]+$/.test(BASE_URL)) throw new Error('BASE_URL은 HTTP(S) URL이어야 한다');
if (!OUT_DIR.startsWith('/')) throw new Error('OUT_DIR은 새로 만든 폴더의 절대 경로여야 한다');
if (!TOKENS_FILE.startsWith('/')) throw new Error('TOKENS_FILE은 절대 경로여야 한다');
if (!['0', '1'].includes(WARMUP)) throw new Error('WARMUP은 0 또는 1이어야 한다');
if (!['0', '1'].includes(FRACTIONAL_OK)) throw new Error('ALLOW_FRACTIONAL_ARRIVAL은 0 또는 1이어야 한다');
if (MAX_VUS < PRE_VUS) throw new Error('MAX_VUS는 PRE_VUS 이상이어야 한다');
if (TARGET_RPS > 1000000 || (TOTAL % TARGET_RPS !== 0 && FRACTIONAL_OK !== '1')) {
	throw new Error('TARGET_RPS는 1,000,000 이하이고 TOTAL을 나누어떨어지게 해야 한다 (나누어떨어지지 않으면 ALLOW_FRACTIONAL_ARRIVAL=1)');
}

const tokens = loadTokens(TOKENS_FILE, TOTAL, WARMUP === '1');
const ARRIVAL_SECONDS = TOTAL / TARGET_RPS;

// B와 같은 종료 경계 보정: 마지막 경계에서 한 건 더 시작되는 일을 피한다.
// 1,000 RPS × 10,000건에서는 9.9995초. 실제 목표 달성은 아래 threshold로 별도 검사한다.
function arrivalDurationMicroseconds(total, rps) {
	// k6의 최소 duration은 1초다. 1초 스모크는 보정하지 않고 초과 실행 가드로 검사한다.
	// 300·600 RPS처럼 total/rps가 정수가 아니면 정수 마이크로초로 반올림한다(나누어떨어지는 값은 이전과 같다).
	return Math.max(1000000, Math.round((total * 1000000) / rps) - Math.floor(500000 / rps));
}
const ARRIVAL_DURATION_US = arrivalDurationMicroseconds(TOTAL, TARGET_RPS);

// 2. 부하: 요청 투입 약 10초 / 각 요청 대기 60초 / 투입 종료 후 여유 65초.
export const options = {
	scenarios: {
		constant_rps_test: {
			executor: 'constant-arrival-rate',
			rate: TARGET_RPS,
			timeUnit: '1s',
			duration: `${ARRIVAL_DURATION_US}us`,
			preAllocatedVUs: PRE_VUS,
			maxVUs: MAX_VUS,
			gracefulStop: `${REQ_TIMEOUT_SECONDS + 5}s`,
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
	thresholds: {
		// 409 전체가 아니라, b-metrics의 201 또는 CR002 분류만 정상이다.
		join_unexpected: ['rate==0'],
		dropped_iterations: ['count==0'],
		http_reqs: [`count==${TOTAL}`],
		iterations: [`count==${TOTAL}`],
		// 성능 합격선은 아직 정하지 않았다. 결과별 지연 분포만 만든다.
		...Object.fromEntries(RESULTS.map((r) => [`join_duration{result:${r}}`, ['p(95)>=0']])),
	},
};

const unexpected = new Rate('join_unexpected');
const responsesDuringArrival = new Counter('join_responses_during_arrival');
const responsesAfterArrival = new Counter('join_responses_after_arrival');
const timeouts = new Counter('join_timeouts');
const successObservedAt = new Trend('join_success_observed_at_ms', true);
const fullObservedAt = new Trend('join_full_observed_at_ms', true);
const scenarioStartedAt = new Gauge('join_scenario_started_at_ms');

// 3. 요청: 실행 번호마다 토큰 하나. 토큰 재사용·재시도·redirect는 하지 않는다.
export default function () {
	const index = exec.scenario.iterationInTest;
	if (index === 0) {
		scenarioStartedAt.add(exec.scenario.startTime);
		// 덤프 예약용: run-db-c-d.py가 이 줄을 k6-c-console.log에 바로 flush한다 (scripts/dump-start-relay.py가 읽음).
		console.log(`SCENARIO_START_MS=${exec.scenario.startTime}`);
	}
	if (index >= TOTAL) {
		unexpected.add(true);
		throw new Error('목표 건수를 넘는 실행 — 추가 HTTP 요청을 보내지 않는다');
	}
	const response = http.post(`${BASE_URL}/crews/${CREW_ID}/join`, null, {
		headers: { Authorization: `Bearer ${tokens[index]}` },
		tags: { name: 'join' },
		timeout: `${REQ_TIMEOUT_SECONDS}s`,
		redirects: 0,
	});
	const elapsedMs = Date.now() - exec.scenario.startTime;
	const result = record(response);
	unexpected.add(result !== '201' && result !== 'cr002');
	// 응답 시각 구간은 HTTP 응답이 있는 경우만 센다. 통신 오류는 별도 분류된다.
	responsesDuringArrival.add(response.status > 0 && elapsedMs <= ARRIVAL_SECONDS * 1000 ? 1 : 0);
	responsesAfterArrival.add(response.status > 0 && elapsedMs > ARRIVAL_SECONDS * 1000 ? 1 : 0);
	timeouts.add(response.error_code === 1050 ? 1 : 0); // transport 분류의 부분집합
	if (result === '201') successObservedAt.add(elapsedMs);
	if (result === 'cr002') fullObservedAt.add(elapsedMs);
}

// 4. 요약: HTTP 지연과 시나리오 시작 이후의 응답 관측 시각을 구분한다.
export function handleSummary(data) {
	const metrics = data.metrics;
	const values = (name) => metrics[name] ? metrics[name].values : {};
	const count = (name) => values(name).count || 0;
	const counts = Object.fromEntries(RESULTS.map((r) => [COUNTER_BY_RESULT[r], count(COUNTER_BY_RESULT[r])]));
	const classified = Object.values(counts).reduce((sum, value) => sum + value, 0);
	const requests = count('http_reqs');
	const iterations = count('iterations');
	const dropped = count('dropped_iterations');
	const valid = requests === TOTAL && iterations === TOTAL && classified === TOTAL &&
		dropped === 0 && values('join_unexpected').rate === 0;
	const report = {
		run_id: RUN_ID, crew_id: CREW_ID, warmup: WARMUP === '1',
		target_rps: TARGET_RPS, total: TOTAL,
		arrival_seconds: ARRIVAL_SECONDS, arrival_duration_us: ARRIVAL_DURATION_US,
		scenario_start_epoch_ms: values('join_scenario_started_at_ms').value ?? null,
		pre_vus: PRE_VUS, max_vus: MAX_VUS,
		request_timeout_seconds: REQ_TIMEOUT_SECONDS, graceful_stop_seconds: REQ_TIMEOUT_SECONDS + 5,
		requests, iterations, dropped_iterations: dropped, counts, classified_total: classified,
		responses_during_arrival: count('join_responses_during_arrival'),
		responses_after_arrival: count('join_responses_after_arrival'),
		timeouts: count('join_timeouts'),
		first_full_response_at_ms: counts.join_cr002 ? values('join_full_observed_at_ms').min : null,
		last_success_response_at_ms: counts.join_201 ? values('join_success_observed_at_ms').max : null,
		load_and_response_checks_passed: valid,
	};
	let stdout = `\n=== DB Only C: ${RUN_ID} / ${CREW_ID}\n`;
	stdout += `목표 ${TARGET_RPS} RPS × ${ARRIVAL_SECONDS}s = ${TOTAL}건 / VU ${PRE_VUS}~${MAX_VUS}\n`;
	stdout += `HTTP ${requests}, iterations ${iterations}, dropped ${dropped}, timeouts ${report.timeouts}\n`;
	stdout += `HTTP 응답: 투입 기간 내 ${report.responses_during_arrival}, 투입 종료 후 ${report.responses_after_arrival}\n`;
	for (const result of RESULTS) {
		const timing = values(`join_duration{result:${result}}`);
		const percentile = (key) => Number.isFinite(timing[key]) && counts[COUNTER_BY_RESULT[result]] ? `${timing[key].toFixed(1)}ms` : '-';
		stdout += `  ${result}: ${counts[COUNTER_BY_RESULT[result]]}, p95=${percentile('p(95)')}, p99=${percentile('p(99)')}\n`;
	}
	stdout += `첫 정원 초과 응답: ${report.first_full_response_at_ms ?? '-'}ms / 마지막 성공 응답: ${report.last_success_response_at_ms ?? '-'}ms\n`;
	stdout += `부하·응답 검사: ${valid ? 'PASS' : 'FAIL'} (최종 DB 정합성은 별도 검증)\n`;
	const file = `${OUT_DIR}/${WARMUP === '1' ? 'warmup-c-summary.json' : 'k6-c-summary.json'}`;
	return { stdout, [file]: JSON.stringify({ ...data, dbc: report }, null, 2) };
}
