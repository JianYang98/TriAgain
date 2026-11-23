// 앱 요청 없이 k6의 도착률 스케줄링을 검증한다: db-baseline-c-d.js의 시나리오 옵션(rate·duration·VU)을 그대로 가져와
// 반복 함수만 빈 함수로 바꾼다. HTTP 요청을 보내지 않으므로 앱·DB·네트워크에 닿지 않는다.
// 확인하는 것: 시작된 반복이 정확히 TOTAL(기본 10,000)건인가, dropped가 0인가, 마지막 시작 인덱스가 TOTAL-1인가.
// 필요한 환경변수는 db-baseline-c-d.js와 같다(BASE_URL은 쓰이지 않는 값, TOKENS_FILE은 형식만 맞는 가짜 파일이어도 된다).
// 결과: $OUT_DIR/schedule-<RPS>.json (RUN_ID 대신 TARGET_RPS로 이름을 정한다).
import exec from 'k6/execution';
import { Gauge } from 'k6/metrics';
import { options as mainOptions } from './db-baseline-c-d.js';

const scenario = mainOptions.scenarios.constant_rps_test;
const TOTAL = Number(__ENV.TOTAL || 10000);

const startOffsetMs = new Gauge('sched_start_offset_ms');
const startIndex = new Gauge('sched_start_index');

export const options = {
	scenarios: { schedule: { ...scenario, exec: 'tick' } },
};

export function tick() {
	startOffsetMs.add(Date.now() - exec.scenario.startTime);
	startIndex.add(exec.scenario.iterationInTest);
}

export function handleSummary(data) {
	const values = (name) => (data.metrics[name] ? data.metrics[name].values : {});
	const iterations = values('iterations').count || 0;
	const dropped = values('dropped_iterations').count || 0;
	const report = {
		target_rps: scenario.rate, total_expected: TOTAL, duration: scenario.duration,
		pre_vus: scenario.preAllocatedVUs, max_vus: scenario.maxVUs,
		iterations, dropped_iterations: dropped,
		first_start_offset_ms: values('sched_start_offset_ms').min,
		last_start_offset_ms: values('sched_start_offset_ms').max,
		max_start_index: values('sched_start_index').max,
		exact_total: iterations === TOTAL && dropped === 0 && values('sched_start_index').max === TOTAL - 1,
	};
	const line = `SCHEDULE rps=${report.target_rps} duration=${report.duration} iterations=${iterations} ` +
		`dropped=${dropped} max_index=${report.max_start_index} last_start_ms=${report.last_start_offset_ms} ` +
		`exact=${report.exact_total ? 'YES' : 'NO'}\n`;
	return { stdout: line, [`${__ENV.OUT_DIR}/schedule-${report.target_rps}.json`]: JSON.stringify(report, null, 2) };
}
