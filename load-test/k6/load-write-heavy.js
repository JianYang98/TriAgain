// ============================================================
// Load Test — Write-Heavy (Day 7, 2026-04-17)
//
// 목적: POST /verifications 단독 부하로 "진짜 쓰기 TPS" 측정
// - sleep 제거 (쓰기 한계 탐색)
// - 매 iteration마다 고유 유저 사용 (409 회피)
// - verify_duplicate > 0 발견 시 유저 풀 소진 시그널 (측정 무효)
//
// 사전조건:
//   SCALE=XXL 시딩 (10k users, 2k crews)
//   tokens.csv에 XXL 유저 토큰 10,000개
//
// 실행:
//   k6 run --env BASE_URL=http://<EC2> --env SCALE=XXL \
//          --env TARGET_VUS=50 --env DURATION=1m load-write-heavy.js
//
// VU 스윕(쉘에서 반복):
//   for VU in 10 30 50 100 150; do
//     psql ... -f 08_reset_api_verifications.sql
//     k6 run --env TARGET_VUS=$VU --env DURATION=1m load-write-heavy.js \
//            --summary-export results/raw/day7_write-${VU}.json \
//            | tee results/raw/day7_write-${VU}.log
//   done
// ============================================================

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import {
	BASE_URL, tokens, getCrewIdByUserIndex, authHeadersByIndex, DATA,
} from './lib/config.js';
import {
	scenarioWriteDuration, verifyCreated, verifyDuplicate,
} from './lib/metrics.js';

const targetVUs = parseInt(__ENV.TARGET_VUS || '30');
const duration = __ENV.DURATION || '1m';

export const options = {
	scenarios: {
		writes_only: {
			executor: 'constant-vus',
			exec: 'writePureExec',
			vus: targetVUs,
			duration: duration,
		},
	},
	thresholds: {
		// POST /verifications 단독 latency (기대 응답만 집계 — 네트워크 오류 제외)
		'http_req_duration{expected_response:true}': ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
		// 모든 요청은 201이어야 함. 409/4xx/5xx는 전부 실패
		'checks{kind:write}': ['rate>0.99'],
		// 서버 측 5xx는 0.1% 미만
		http_req_failed: ['rate<0.001'],
	},
};

/**
 * 매 iteration마다 iterationInTest를 유저풀 크기로 모듈로 → 고유 유저 분배.
 * VU × duration × ~TPS 조합이 DATA.users를 넘지 않아야 409가 안 터진다.
 *
 * 예) TARGET_VUS=100, duration=1m, 예상 쓰기 TPS 100/s → 6000 iter
 *     → DATA.users(XXL: 10000) 이내라 안전
 */
export function writePureExec() {
	const userIndex = exec.scenario.iterationInTest % DATA.users;
	const params = authHeadersByIndex(userIndex);
	const crewId = getCrewIdByUserIndex(userIndex);

	const payload = JSON.stringify({
		crewId: crewId,
		textContent: `day7 write-heavy iter-${exec.scenario.iterationInTest}`,
	});

	const res = http.post(`${BASE_URL}/verifications`, payload, params);
	scenarioWriteDuration.add(res.timings.duration);

	if (res.status === 201) {
		verifyCreated.add(1);
	} else if (res.status === 409) {
		verifyDuplicate.add(1);
	}

	// tag "kind:write"를 붙여서 thresholds에서 이 체크만 골라낼 수 있도록 함
	check(res, {
		'write: 201 created': (r) => r.status === 201,
	}, { kind: 'write' });
}
