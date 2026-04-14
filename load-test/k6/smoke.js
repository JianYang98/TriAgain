// ============================================================
// Smoke Test — 스크립트 정상 동작 확인
// k6 run --env BASE_URL=http://localhost:8080 --env SCALE=S smoke.js
// ============================================================

import { crewHome } from './lib/scenarios.js';

export const options = {
	vus: 2,
	duration: '30s',
	thresholds: {
		http_req_duration: ['p(95)<500'],
		http_req_failed: ['rate<0.05'],
		scenario_a_duration: ['p(95)<500'],
	},
};

export default function () {
	crewHome();
}
