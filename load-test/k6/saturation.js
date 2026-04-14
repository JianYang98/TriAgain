// ============================================================
// Saturation Test — 포화점 탐색
//
// VUser 10 -> 50 -> 100 -> 200 -> 300으로 점진 증가.
// TPS가 더 이상 올라가지 않거나 떨어지는 지점이 포화점.
// 비율: A:B = 90:10 (평상시)
//
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=L saturation.js
// ============================================================

import { crewHome, writeScenario } from './lib/scenarios.js';

export const options = {
	scenarios: {
		reads: {
			executor: 'ramping-vus',
			exec: 'readScenario',
			startVUs: 0,
			stages: [
				{ duration: '1m', target: 9 },
				{ duration: '2m', target: 45 },
				{ duration: '2m', target: 90 },
				{ duration: '2m', target: 180 },
				{ duration: '2m', target: 270 },
				{ duration: '1m', target: 0 },
			],
		},
		writes: {
			executor: 'ramping-arrival-rate',
			exec: 'writeExec',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 50,
			maxVUs: 200,
			stages: [
				{ duration: '1m', target: 1 },
				{ duration: '2m', target: 5 },
				{ duration: '2m', target: 10 },
				{ duration: '2m', target: 20 },
				{ duration: '2m', target: 30 },
				{ duration: '1m', target: 0 },
			],
		},
	},
	thresholds: {
		http_req_duration: ['p(95)<200', 'p(99)<500'],
		http_req_failed: ['rate<0.01'],
		scenario_a_duration: ['p(95)<200'],
		scenario_b_duration: ['p(95)<500'],
	},
};

export function readScenario() {
	crewHome();
}

export function writeExec() {
	writeScenario();
}
