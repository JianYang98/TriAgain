// ============================================================
// Dup Join Test — 시나리오 E (전략 C 중복 가입 동시성 검증)
//
// 같은 토큰으로 N발 동시 가입 → 멤버십 1건 + 나머지 409.
//
// 사전 준비:
//   1. DUP_CREW_ID: PUBLIC RECRUITING 크루 1개
//   2. DUP_TOKEN: 해당 유저의 accessToken (가입 전 상태)
//
// k6 run \
//   --env BASE_URL=http://<SERVER> \
//   --env DUP_CREW_ID=<crewId> \
//   --env DUP_TOKEN=<accessToken> \
//   --env TARGET_VUS=20 \
//   dup-join.js
//
// 기대 결과:
//   dup_join_success == 1
//   dup_join_already == TARGET_VUS - 1
// ============================================================

import { dupJoin } from './lib/scenarios.js';

const vus = parseInt(__ENV.TARGET_VUS || '20');

export const options = {
	scenarios: {
		dup: {
			executor: 'per-vu-iterations',
			exec: 'dupExec',
			vus: vus,
			iterations: 1,
			maxDuration: '30s',
		},
	},
	thresholds: {
		http_req_duration: ['p(95)<1000'],
		scenario_e_duration: ['p(95)<1000'],
		dup_join_success: ['count==1'],
		dup_join_already: [`count==${vus - 1}`],
	},
};

export function dupExec() {
	dupJoin();
}

export { handleSummary } from './lib/report.js';
