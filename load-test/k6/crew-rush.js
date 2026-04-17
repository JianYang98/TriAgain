// ============================================================
// Crew Rush Test — 시나리오 D (동시성 검증)
//
// 50 VUs가 정원 10명 크루에 동시 참가 시도.
// SELECT FOR UPDATE 경합 + 정원 초과 처리 검증.
//
// 실행 전 반드시:
//   psql ... -f load-test/sql/07_rush_reset.sql
//
// k6 run --env BASE_URL=http://<SERVER> crew-rush.js
// k6 run --env BASE_URL=http://<SERVER> --env TARGET_VUS=100 --env RUSH_CREW_COUNT=1 crew-rush.js
// ============================================================

import { crewRush } from './lib/scenarios.js';

const vus = parseInt(__ENV.TARGET_VUS || '50');
const maxMembers = 10;
const expectedFull = vus - maxMembers;

export const options = {
	scenarios: {
		rush: {
			executor: 'per-vu-iterations',
			exec: 'rushExec',
			vus: vus,
			iterations: 1,
			maxDuration: '30s',
		},
	},
	thresholds: {
		http_req_duration: ['p(95)<1000'],
		scenario_d_duration: ['p(95)<1000'],
		join_success: [`count==${maxMembers}`],
		join_full: [`count==${expectedFull}`],
	},
};

export function rushExec() {
	crewRush();
}

export { handleSummary } from './lib/report.js';
