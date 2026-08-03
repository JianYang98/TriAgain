// ============================================================
// Load Test — 평상시 (A:B = 90:10)
//
// 기본: ramping stages (10 -> 50 -> 100 -> 200 -> cool-down)
// VUser 고정: --env TARGET_VUS=50 --env DURATION=2m
//
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=L load-normal.js
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=L --env TARGET_VUS=50 --env DURATION=2m load-normal.js
// ============================================================

import { crewHome, writeScenario } from './lib/scenarios.js';

const targetVUs = __ENV.TARGET_VUS ? parseInt(__ENV.TARGET_VUS) : 0;
const duration = __ENV.DURATION || '2m';

export const options = {
	scenarios: {
		reads: targetVUs
			? {
				executor: 'constant-vus',
				exec: 'readScenario',
				vus: Math.round(targetVUs * 0.9) || 1,
				duration: duration,
			}
			: {
				executor: 'ramping-vus',
				exec: 'readScenario',
				startVUs: 0,
				stages: [
					{ duration: '1m', target: 9 },
					{ duration: '2m', target: 45 },
					{ duration: '2m', target: 90 },
					{ duration: '2m', target: 180 },
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
			stages: targetVUs
				? [{ duration: duration, target: Math.round(targetVUs * 0.1) || 1 }]
				: [
					{ duration: '1m', target: 1 },
					{ duration: '2m', target: 5 },
					{ duration: '2m', target: 10 },
					{ duration: '2m', target: 20 },
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

export { handleSummary } from './lib/report.js';
