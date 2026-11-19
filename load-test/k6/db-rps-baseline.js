// prepare로 생성한 크루의 공개 가입 경로를 일정한 RPS로 측정한다.
// 러너 설정: BASE_URL, TOKENS_FILE, RESULTS_ROOT, PRE_VUS (MAX_VUS 선택).
// 회차 입력: TARGET_RPS, TOTAL, RUN_ID. 토큰 파일에서 앞 TOTAL개를 쓴다.
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

const RUN_ID = readEnv('RUN_ID');
const run = /^(\d{8}-\d{6})-rps([1-9][0-9]*)-cap[1-9][0-9]*(-warmup)?$/.exec(RUN_ID);
if (!run) throw new Error('RUN_ID는 prepare 출력의 실행 ID여야 한다');

const CREW_ID = `dbb-${run[1]}`;
const TARGET_RPS = positiveInteger('TARGET_RPS');
const TOTAL = positiveInteger('TOTAL');
const WARMUP = Boolean(run[3]);
if (TARGET_RPS !== Number(run[2])) throw new Error('TARGET_RPS가 RUN_ID에 기록된 목표 RPS와 다르다');

const BASE_URL = readEnv('BASE_URL').replace(/\/+$/, '');
const TOKENS_FILE = readEnv('TOKENS_FILE');
const RESULTS_ROOT = readEnv('RESULTS_ROOT');
const PRE_VUS = positiveInteger('PRE_VUS');
const MAX_VUS = positiveInteger('MAX_VUS', PRE_VUS);
const REQ_TIMEOUT_SECONDS = positiveInteger('REQ_TIMEOUT_SECONDS', 60);
const OUT_DIR = `${RESULTS_ROOT.replace(/\/+$/, '')}/${RUN_ID}`;

if (!/^https?:\/\/[^\s]+$/.test(BASE_URL)) throw new Error('BASE_URL은 HTTP(S) URL이어야 한다');
if (!RESULTS_ROOT.startsWith('/')) throw new Error('RESULTS_ROOT는 절대 경로여야 한다');
if (!TOKENS_FILE.startsWith('/')) throw new Error('TOKENS_FILE은 절대 경로여야 한다');
if (MAX_VUS < PRE_VUS) throw new Error('MAX_VUS는 PRE_VUS 이상이어야 한다');
if (TARGET_RPS > 1000000 || TOTAL < TARGET_RPS || TOTAL > Math.floor(Number.MAX_SAFE_INTEGER / 1000000)) {
	throw new Error('TARGET_RPS는 1,000,000 이하, TOTAL은 최소 1초 분량이며 시간 계산 범위 안이어야 한다');
}

// 기존 로더에서 true는 최소 TOTAL개를 요구하고 앞 TOTAL개만 반환한다.
const tokens = loadTokens(TOKENS_FILE, TOTAL, true);
const ARRIVAL_SECONDS = TOTAL / TARGET_RPS;

// B와 같은 종료 경계 보정: 마지막 경계에서 한 건 더 시작되는 일을 피한다.
// 1,000 RPS × 10,000건에서는 9.9995초. 실제 목표 달성은 아래 threshold로 별도 검사한다.
function arrivalDurationMicroseconds(total, rps) {
	// k6의 최소 duration은 1초다. 1초 스모크는 보정하지 않고 초과 실행 가드로 검사한다.
	// 300·600 RPS처럼 total/rps가 정수가 아니면 정수 마이크로초로 반올림한다(나누어떨어지는 값은 이전과 같다).
	return Math.max(1000000, Math.round((total * 1000000) / rps) - Math.floor(500000 / rps));
}
const ARRIVAL_DURATION_US = arrivalDurationMicroseconds(TOTAL, TARGET_RPS);

// 2. 부하: 목표 RPS와 요청 수로 투입 시간을 계산한다.
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
const unauthorized = new Counter('join_http_401');
const successObservedAt = new Trend('join_success_observed_at_ms', true);
const fullObservedAt = new Trend('join_full_observed_at_ms', true);
const scenarioStartedAt = new Gauge('join_scenario_started_at_ms');

// 3. 요청: 실행 번호마다 토큰 하나. 토큰 재사용·재시도·redirect는 하지 않는다.
export default function () {
	const index = exec.scenario.iterationInTest;
	if (index === 0) {
		scenarioStartedAt.add(exec.scenario.startTime);
		// 수집 결과의 시간축과 대조할 요청 투입 시작 시각.
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
	if (response.status === 401) unauthorized.add(1);
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
	const unexpectedCount = values('join_unexpected').passes || 0;
	const failureReasons = [];
	if (dropped > 0) failureReasons.push(`사용 가능한 VU 부족으로 ${dropped}건 미전송 (VU 한도 ${MAX_VUS}개)`);
	if (requests !== TOTAL || iterations !== TOTAL) {
		failureReasons.push(`목표 ${TOTAL}건 / HTTP 요청 ${requests}건 / 완료한 반복 ${iterations}건`);
	}
	if (unexpectedCount > 0) {
		failureReasons.push(`예상 밖 결과 ${unexpectedCount}건 (HTTP 401 ${count('join_http_401')}건)`);
	}
	if (classified !== requests) {
		failureReasons.push(`HTTP 요청 ${requests}건 중 응답 분류 기록 ${classified}건`);
	}
	const valid = requests === TOTAL && iterations === TOTAL && classified === TOTAL &&
		dropped === 0 && values('join_unexpected').rate === 0;
	if (!valid && failureReasons.length === 0) failureReasons.push('응답 검사 지표 없음');
	const report = {
		run_id: RUN_ID, crew_id: CREW_ID, warmup: WARMUP,
		target_rps: TARGET_RPS, total: TOTAL,
		arrival_seconds: ARRIVAL_SECONDS, arrival_duration_us: ARRIVAL_DURATION_US,
		scenario_start_epoch_ms: values('join_scenario_started_at_ms').value ?? null,
		pre_vus: PRE_VUS, max_vus: MAX_VUS,
		observed_max_vus: values('vus').max ?? null,
		actual_average_rps: requests * 1000000 / ARRIVAL_DURATION_US,
		request_timeout_seconds: REQ_TIMEOUT_SECONDS, graceful_stop_seconds: REQ_TIMEOUT_SECONDS + 5,
		requests, iterations, dropped_iterations: dropped, counts, classified_total: classified,
		responses_during_arrival: count('join_responses_during_arrival'),
		responses_after_arrival: count('join_responses_after_arrival'),
		timeouts: count('join_timeouts'),
		http_401: count('join_http_401'),
		failure_reasons: failureReasons,
		first_full_response_at_ms: counts.join_cr002 ? values('join_full_observed_at_ms').min : null,
		last_success_response_at_ms: counts.join_201 ? values('join_success_observed_at_ms').max : null,
		load_and_response_checks_passed: valid,
	};
	let stdout = `\n=== DB RPS Baseline: ${RUN_ID} / ${CREW_ID}\n`;
	stdout += `투입: 목표 ${TARGET_RPS} RPS / 실제 평균 ${report.actual_average_rps.toFixed(1)} RPS / ${(ARRIVAL_DURATION_US / 1000000).toFixed(3)}초\n`;
	stdout += `요청: 목표 ${TOTAL}건 / 전송 ${requests}건 / 미전송 ${dropped}건 / 반복 ${iterations}건\n`;
	stdout += `VU: 사전 할당 ${PRE_VUS}개 / 관측 최대 ${report.observed_max_vus ?? '-'}개 / 한도 ${MAX_VUS}개\n`;
	stdout += `HTTP 401: ${report.http_401}건 (기타 4xx에 포함) / 타임아웃: ${report.timeouts}건\n`;
	stdout += `HTTP 응답: 투입 기간 내 ${report.responses_during_arrival}, 투입 종료 후 ${report.responses_after_arrival}\n`;
	for (const result of RESULTS) {
		const timing = values(`join_duration{result:${result}}`);
		const percentile = (key) => Number.isFinite(timing[key]) && counts[COUNTER_BY_RESULT[result]] ? `${timing[key].toFixed(1)}ms` : '-';
		stdout += `  ${result}: ${counts[COUNTER_BY_RESULT[result]]}, p95=${percentile('p(95)')}, p99=${percentile('p(99)')}\n`;
	}
	stdout += `첫 정원 초과 응답: ${report.first_full_response_at_ms ?? '-'}ms / 마지막 성공 응답: ${report.last_success_response_at_ms ?? '-'}ms\n`;
	stdout += `부하·응답 검사: ${valid ? 'PASS' : 'FAIL'} (최종 DB 정합성은 별도 검증)\n`;
	for (const reason of failureReasons) stdout += `실패 사유: ${reason}\n`;
	const summaryFile = `${OUT_DIR}/k6-rps-summary.json`;
	const textOutput = __ENV.RUNNER_FOOTER === '1' ? `${OUT_DIR}/.k6-summary.txt` : 'stdout';
	return { [textOutput]: stdout, [summaryFile]: JSON.stringify({ ...data, db_rps_baseline: report }, null, 2) };
}
