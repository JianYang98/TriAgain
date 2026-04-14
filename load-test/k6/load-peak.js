// ============================================================
// Load Test — 마감 피크 (A:B = 40:60)
//
// 마감 직전 인증 몰림 시뮬레이션: write 비율 60%
//
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=L load-peak.js
// k6 run --env BASE_URL=http://<SERVER> --env SCALE=L --env TARGET_VUS=100 --env DURATION=2m load-peak.js
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
				vus: Math.round(targetVUs * 0.4) || 1,
				duration: duration,
			}
			: {
				executor: 'ramping-vus',
				exec: 'readScenario',
				startVUs: 0,
				stages: [
					{ duration: '1m', target: 4 },
					{ duration: '2m', target: 20 },
					{ duration: '2m', target: 40 },
					{ duration: '2m', target: 80 },
					{ duration: '1m', target: 0 },
				],
			},
		writes: {
			executor: 'ramping-arrival-rate',
			exec: 'writeExec',
			startRate: 3,
			timeUnit: '1s',
			preAllocatedVUs: 100,
			maxVUs: 300,
			stages: targetVUs
				? [{ duration: duration, target: Math.round(targetVUs * 0.6) || 1 }]
				: [
					{ duration: '1m', target: 6 },
					{ duration: '2m', target: 30 },
					{ duration: '2m', target: 60 },
					{ duration: '2m', target: 120 },
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
