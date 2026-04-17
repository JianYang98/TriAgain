// ============================================================
// Stress Test — Breaking Point 탐색
//
// VUser 100 -> 300 -> 500. 서버가 어디서 무너지는지 확인.
// thresholds를 완화하여 테스트 자체는 실패하지 않게 함.
//
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=XL stress.js
// ============================================================

import { crewHome, writeScenario } from './lib/scenarios.js';

export const options = {
	scenarios: {
		reads: {
			executor: 'ramping-vus',
			exec: 'readScenario',
			startVUs: 0,
			stages: [
				{ duration: '1m', target: 90 },
				{ duration: '2m', target: 270 },
				{ duration: '2m', target: 450 },
				{ duration: '1m', target: 0 },
			],
		},
		writes: {
			executor: 'ramping-arrival-rate',
			exec: 'writeExec',
			startRate: 10,
			timeUnit: '1s',
			preAllocatedVUs: 100,
			maxVUs: 500,
			stages: [
				{ duration: '1m', target: 10 },
				{ duration: '2m', target: 30 },
				{ duration: '2m', target: 50 },
				{ duration: '1m', target: 0 },
			],
		},
	},
	thresholds: {
		http_req_duration: ['p(95)<500'],
		http_req_failed: ['rate<0.05'],
	},
};

export function readScenario() {
	crewHome();
}

export function writeExec() {
	writeScenario();
}

export { handleSummary } from './lib/report.js';
