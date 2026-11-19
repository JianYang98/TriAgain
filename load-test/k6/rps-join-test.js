// 생성한 크루의 공개 가입 경로로 일정한 RPS로 가입을 요청하는 스크립트
// 러너 설정: BASE_URL, TOKENS_FILE, RESULTS_ROOT, PRE_VUS (MAX_VUS 선택)
// 회차 입력: TARGET_RPS(목표 RPS), REQUESTS( 총 요청수), RUN_ID를 입력한다.

// Rate: 조건을 만족한 비율 (true / 전체 기록)
// Counter: 누적 발생 
// Trend: 측정값의 분포 (평균, 최솟값, 최댓값, p95 등)
// Gauge: 기록된 시점의 값 (마지막 값, 최솟값, 최댓값 등)

import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';
import { loadTokens } from './lib/b-tokens.js';
import { COUNTER_BY_RESULT, RESULTS, record } from './lib/b-metrics.js';

// 외부에서 전달된 환경변수를 샤샤샥 읽고, 필수 환경변수의 누락 여부를 검증
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


//환경 변수 세팅 
const RUN_ID = readEnv('RUN_ID');

// RUN_ID에서 크루 ID를 만드는 데 쓸 일시와 TARGET_RPS를 대조할 RPS를 읽는다.
const run = /^(\d{8}-\d{6})-rps([1-9][0-9]*)-cap[1-9][0-9]*(-warmup)?$/.exec(RUN_ID);
if (!run) {
  throw new Error(
    'prepare가 출력한 RUN_ID를 사용하세요.\n수동 입력일때 형식은 YYYYMMDD-HHMMSS-rps<RPS>-cap<정원>이며, 워밍업에는 -warmup이 붙습니다.'
  );
}


// RUN_ID에서 크루 식별자를 추출해 가입 대상 크루 ID를 확정
const CREW_ID = `dbb-${run[1]}`;

// 초당 목표 반복 시작 횟수 
const TARGET_RPS = positiveInteger('TARGET_RPS');

// 테스트에서 전송할 총 요청양
const REQUESTS = positiveInteger('REQUESTS');

// RUN_ID에 '-warmup'이 포함되어 있는지 확인
const WARMUP = Boolean(run[3]);

// RUN_ID에 기록된 RPS와 외부에서 전달한 목표 RPS가 일치하는지 검증
if (TARGET_RPS !== Number(run[2])) {
    throw new Error('TARGET_RPS가 RUN_ID에 기록된 목표 RPS와 다릅니다.');
}

// 부하 테스트 대상 서버의 기본 URL 끝 정리
const BASE_URL = readEnv('BASE_URL').replace(/\/+$/, '');

// 가입 요청에 사용할 사용자 인증 토큰 파일 경로
const TOKENS_FILE = readEnv('TOKENS_FILE');

// 테스트 결과 파일을 저장할 최상위 디렉터리 경로
const RESULTS_ROOT = readEnv('RESULTS_ROOT');

// 테스트 시작 전에 미리 할당할 VU 수
const PRE_VUS = positiveInteger('PRE_VUS');

// 실행 중 허용할 최대 VU 수 (기본값: PRE_VUS)
const MAX_VUS = positiveInteger('MAX_VUS', PRE_VUS);

// 개별 HTTP 요청의 타임아웃 시간 (초, 기본값: 60초)
const REQ_TIMEOUT_SECONDS = positiveInteger('REQ_TIMEOUT_SECONDS', 60);

// RUN_ID 저장 경로 (load-test/results/<SESSION_DATE>/<RUN_ID>)
const OUT_DIR = `${RESULTS_ROOT.replace(/\/+$/, '')}/${RUN_ID}`;

// BASE_URL 유효성 검사 
if (!/^https?:\/\/[^\s]+$/.test(BASE_URL)){
	throw new Error('BASE_URL은 HTTP(S) URL이어야 한다');
} 

// 테스트 실행 전 파일 경로, VU 설정, 요청 수와 RPS의 허용 범위를 검증한다.
if (!RESULTS_ROOT.startsWith('/')) throw new Error('RESULTS_ROOT는 절대 경로여야 한다');
if (!TOKENS_FILE.startsWith('/')) throw new Error('TOKENS_FILE은 절대 경로여야 한다');
if (MAX_VUS < PRE_VUS) throw new Error('MAX_VUS는 PRE_VUS 이상이어야 한다');

if (TARGET_RPS > 1000000 || REQUESTS < TARGET_RPS || REQUESTS > Math.floor(Number.MAX_SAFE_INTEGER / 1000000)) {
    throw new Error('TARGET_RPS는 1,000,000 이하, REQUESTS는 최소 1초 분량이며 시간 계산 범위 안이어야 한다');
}


// 총 요청 수 만큼 토큰 로더
const tokens = loadTokens(TOKENS_FILE, REQUESTS, true);

// 종료 시간 계산
const ARRIVAL_SECONDS = REQUESTS / TARGET_RPS;

// 목표 요청 수와 RPS로 테스트 실행 시간을 계산
// 종료 경계에서 추가 반복이 시작되지 않도록 요청 시간을 조정
function arrivalDurationMicroseconds(total, rps) {
	// k6의 최소 duration은 1초다. 1초 스모크는 보정하지 않고 초과 실행 가드로 검사
	// 300·600 RPS처럼 total/rps가 정수가 아니면 정수 마이크로초로 반올림한다(나누어떨어지는 값은 이전과 같다).
	return Math.max(1000000, Math.round((total * 1000000) / rps) - Math.floor(500000 / rps));
}
const ARRIVAL_DURATION_US = arrivalDurationMicroseconds(REQUESTS, TARGET_RPS);

// 2. 부하: 목표 RPS와 요청 수로 투입 시간을 계산한다.
export const options = {
	scenarios: {
		constant_rps_test: {
			// 초당 TARGET_RPS건의 가입 요청을 시작
			executor: 'constant-arrival-rate',
			rate: TARGET_RPS, // 목표 RPS 
			timeUnit: '1s',
			duration: `${ARRIVAL_DURATION_US}us`, //  새 요청을 투입하는 기간
			preAllocatedVUs: PRE_VUS,
			maxVUs: MAX_VUS,
			gracefulStop: `${REQ_TIMEOUT_SECONDS + 5}s`, // 진행 중인 반복의 종료 유예 시간
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
	thresholds: {
		// 409 전체가 아니라, b-metrics의 201 또는 CR002 분류만 정상!
		join_unexpected: ['rate==0'],
		dropped_iterations: ['count==0'],
		http_reqs: [`count==${REQUESTS}`],
		iterations: [`count==${REQUESTS}`],
		
		...Object.fromEntries(RESULTS.map((r) => [`join_duration{result:${r}}`, ['p(95)>=0']])),
	},
};



// 예상 밖 응답 비율 (201, CR002 외의 결과)
const unexpected = new Rate('join_unexpected');

// 요청 투입 기간 내에 관측된 HTTP 응답 건수
const responsesDuringArrival = new Counter('join_responses_during_arrival');

// 요청 투입 기간 종료 후 관측된 HTTP 응답 건수
const responsesAfterArrival = new Counter('join_responses_after_arrival');

// HTTP 요청 타임아웃 발생 건수 (에러 코드 1050)
const timeouts = new Counter('join_timeouts');

// HTTP 401(인증 실패) 응답 건수 (토큰 확인 하기)
const unauthorized = new Counter('join_http_401');

// 가입 성공(201) 응답을 관측한 시각 (시나리오 시작 후 경과 시간, ms)
const successObservedAt = new Trend('join_success_observed_at_ms', true);

// 정원 초과(CR002) 응답을 관측한 시각 (시나리오 시작 후 경과 시간, ms)
const fullObservedAt = new Trend('join_full_observed_at_ms', true);

// 시나리오 시작 시각 (타임스탬프, ms)
const scenarioStartedAt = new Gauge('join_scenario_started_at_ms');


// 3. 크루 가입 요청 실행 및 응답 측정
// 반복마다 고유한 사용자 토큰으로 가입 요청을 전송한다.
// 목표 요청 수를 초과하지 않도록 검사하고, 응답 결과와 응답 관측 시각을 메트릭으로 기록한다.
export default function () {

	//  현재 시나리오의 반복 고유 번호를 가져와 사용자별 토큰 선택에 사용한다.
	const index = exec.scenario.iterationInTest;

	if (index === 0) {  // 첫 번째 반복에서 시나리오 시작 시각을 기록한다.
		scenarioStartedAt.add(exec.scenario.startTime);
		// 수집 결과의 시간축과 대조할 요청 투입 시작 시각.
		console.log(`SCENARIO_START_MS=${exec.scenario.startTime}`);
	}
	 // 목표 요청 수를 초과하는 반복에서는 HTTP 요청을 보내지 않는다.
	if (index >= REQUESTS) {
		unexpected.add(true);
		throw new Error('목표 건수를 넘는 실행 — 추가 HTTP 요청을 보내지 않는다');
	}

	// 크루 가입 요청
	const response = http.post(`${BASE_URL}/crews/${CREW_ID}/join`, null, {
		headers: { Authorization: `Bearer ${tokens[index]}` }, // 고유번호로 토큰 선택
		tags: { name: 'join' },
		timeout: `${REQ_TIMEOUT_SECONDS}s`,
		redirects: 0,
	});

	const elapsedMs = Date.now() - exec.scenario.startTime;
	const result = record(response);

	// 가입 성공(201)과 정원 초과(CR002) 외의 결과를 예상 밖 응답으로 분류
	const isUnexpected = result !== '201' && result !== 'cr002';
	unexpected.add(isUnexpected);

	
	// HTTP 401 응답 기록
	if (response.status === 401) {
			unauthorized.add(1);
	}

	// 응답 시각 구간은 HTTP 응답이 있는 경우 집계
	if (response.status > 0) {
			if (elapsedMs <= ARRIVAL_SECONDS * 1000) {
					responsesDuringArrival.add(1);
			} else {
					responsesAfterArrival.add(1);
			}
	}

	// 타임아웃 발생 기록
	if (response.error_code === 1050) {
			timeouts.add(1);
	}

	// 가입 성공 기록
	if (result === '201') {
			successObservedAt.add(elapsedMs);
	}

	// 정원 초과 기록
	if (result === 'cr002') {
			fullObservedAt.add(elapsedMs);
	}

}

// 4. 요약: HTTP 지연과 시나리오 시작 이후의 응답 관측 시각을 구분
export function handleSummary(data) {
	
	//  테스트 결과에서 메트릭 추출
	const metrics = data.metrics;

	// 메트릭 이름으로 측정 값 조회 ( 없으면 빈 객체)
	const values = (name) => metrics[name] ? metrics[name].values : {};

	// 메트릭의 누적 건수 조회 
	const count = (name) => values(name).count || 0;

	// 결과별 응답 건수 저장 ex) { join_201: 50, join_cr002: 1950 }
	const counts = {};
	for (const r of RESULTS) {
			const name = COUNTER_BY_RESULT[r];
			counts[name] = count(name);
	}

	// 분류된 응답 건수의 총합
	const classified = Object.values(counts).reduce((sum, value) => sum + value, 0);

	// 실행 건수
	const requests = count('http_reqs');
	const iterations = count('iterations');
	const dropped = count('dropped_iterations');

	// 예상 밖 응답 건수 (Rate에서 true로 기록된 건수)
  const unexpectedCount = values('join_unexpected').passes ?? 0;

	const unexpectedRate = values('join_unexpected').rate;

	// 검증 실패 사유. 하나라도 있으면 FAIL
	const failureReasons = [];


	if (dropped > 0) {
			failureReasons.push(`사용 가능한 VU 부족으로 ${dropped}건 미전송 (VU 한도 ${MAX_VUS}개)`);
	}
	if (requests !== REQUESTS || iterations !== REQUESTS) {
			failureReasons.push(`목표 ${REQUESTS}건 / HTTP 요청 ${requests}건 / 완료한 반복 ${iterations}건`);
	}
	if (unexpectedCount > 0) {
			failureReasons.push(`예상 밖 결과 ${unexpectedCount}건 (HTTP 401 ${count('join_http_401')}건)`);
	}
	if (classified !== requests) {
			failureReasons.push(`HTTP 요청 ${requests}건 중 응답 분류 기록 ${classified}건`);
	}
	if (unexpectedCount === 0 && unexpectedRate !== 0) {
			failureReasons.push('응답 검사 지표 없음');   // Rate 지표가 비었거나 읽을 수 없는 경우
	}

	// 검증 실패 사유가 없으면 합격 
	const valid = failureReasons.length === 0;     

	// 테스트 설정값, 측정 결과, 검증 결과를 JSON 보고서 객체 구성
	const report = {
			run_id: RUN_ID, crew_id: CREW_ID, warmup: WARMUP,
			target_rps: TARGET_RPS, total: REQUESTS,
			arrival_seconds: ARRIVAL_SECONDS, arrival_duration_us: ARRIVAL_DURATION_US,
			scenario_start_epoch_ms: values('join_scenario_started_at_ms').value ?? null,
			pre_vus: PRE_VUS, max_vus: MAX_VUS,
			observed_max_vus: values('vus').max ?? null,
			actual_average_rps: requests * 1000000 / ARRIVAL_DURATION_US, //  전체 HTTP 요청 수를 설정된 투입 기간으로 나눈 계산값
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


	// 콘솔에 출력할 문자열 생성
	let stdout = `\n=== rps-join-test  : ${RUN_ID} / ${CREW_ID}\n`;

	// 부하 설정 및 실행 결과
	stdout += `투입: 목표 ${TARGET_RPS} RPS / 실제 평균 ${report.actual_average_rps.toFixed(1)} `;
	stdout += `RPS / ${(ARRIVAL_DURATION_US / 1000000).toFixed(3)}초\n`;
	stdout += `요청: 목표 ${REQUESTS}건 / 전송 ${requests}건 / 미전송 ${dropped}건 / 반복 ${iterations}건\n`;
	stdout += `VU: 사전 할당 ${PRE_VUS}개 / 관측 최대 ${report.observed_max_vus ?? '-'}개 / 한도 ${MAX_VUS}개\n`;
	stdout += `HTTP 401: ${report.http_401}건 (기타 4xx에 포함) / 타임아웃: ${report.timeouts}건\n`;
	stdout += `HTTP 응답: 투입 기간 내 ${report.responses_during_arrival}, `;
	stdout += `투입 종료 후 ${report.responses_after_arrival}\n`;


	// 응답 결과별 건수와 avg, p50(med), p95, p99 지연시간 출력
	for (const result of RESULTS) {

    const resultCount = counts[COUNTER_BY_RESULT[result]];
    const timing = values(`join_duration{result:${result}}`);

    // 측정값을 소수점 첫째 자리의 ms 문자열 변환
		// 측정값이 유효하지 않거나 해당 응답이 0건이면 '-'를 반환한다.
    const formatMs = (key) => {
        const durationMs = timing[key];
        if (!Number.isFinite(durationMs) || !resultCount) {
            return '-';
        }
        return `${durationMs.toFixed(1)}ms`;
    };

    stdout += `  ${result}: ${resultCount}, avg=${formatMs('avg')}, `;
		stdout += `p50=${formatMs('med')}, p95=${formatMs('p(95)')}, p99=${formatMs('p(99)')}\n`;
	}



 
	stdout += `첫 정원 초과 응답: ${report.first_full_response_at_ms ?? '-'}ms / 마지막 성공 응답: ${report.last_success_response_at_ms ?? '-'}ms\n`;
	stdout += `부하·응답 검사: ${valid ? 'PASS' : 'FAIL'} (최종 DB 정합성은 별도 검증)\n`;
	// 검증에 실패한 사유를 한 줄씩 출력한다.
	for (const reason of failureReasons) {
			stdout += `실패 사유: ${reason}\n`;
	}

	// 콘솔 요약과 JSON 보고서 저장 경로
	const summaryTextFile = `${OUT_DIR}/k6-summary.txt`;
	const summaryFile = `${OUT_DIR}/k6-rps-summary.json`;

	// 콘솔 출력 + 텍스트 파일 + JSON 파일 동시 저장
	return {
					stdout: stdout,
					[summaryTextFile]: stdout,
					[summaryFile]: JSON.stringify({ ...data, db_rps_baseline: report },null,2)
				};


}
