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
// ⚠️ DURATION은 유저풀에 종속된 값이다. 임의로 늘리지 말 것 (아래 계산식 참조).
//    Day 7 실측은 전부 10~15s였고, 1m로 돌리면 약 20s에 유저풀이 소진되어
//    남은 구간이 전부 409가 되며 측정이 무효화된다.
//
// 실행:
//   k6 run --env BASE_URL=http://<EC2> --env SCALE=XXL \
//          --env TARGET_VUS=50 --env DURATION=15s load-write-heavy.js
//
// VU 스윕(쉘에서 반복):
//   for VU in 10 30 50 100 150; do
//     psql ... -f 08_reset_api_verifications.sql
//     k6 run --env TARGET_VUS=$VU --env DURATION=15s load-write-heavy.js \
//            --summary-export results/raw/day7_write-${VU}.json \
//            | tee results/raw/day7_write-${VU}.log
//   done
// ============================================================

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import {
	BASE_URL, tokens, getCrewIdByUserIndex, authHeadersByIndex, DATA, SCALE,
} from './lib/config.js';
import {
	scenarioWriteDuration, verifyCreated, verifyDuplicate,
} from './lib/metrics.js';

// [신규] 헤더 9~11줄의 "사전조건"을 문서가 아니라 코드로 강제한다.
// init 컨텍스트에서 throw하면 VU가 하나도 뜨기 전에 k6가 비0으로 종료한다.
if (!DATA) {
	throw new Error(
		`[load-write-heavy] 알 수 없는 SCALE='${SCALE}'. S/M/L/XL/XXL 중 하나여야 한다 (lib/config.js SCALE_MAP).`
	);
}
if (DATA.users < 10000) {
	throw new Error(
		`[load-write-heavy] 이 스크립트는 SCALE=XXL 전제다 (현재 '${SCALE}', users=${DATA.users}). ` +
		`--env SCALE=XXL 를 붙여라. 기본값은 'S'(users=50)라 즉시 유저풀이 소진된다.`
	);
}
// 토큰 배열은 tokens.length로, 크루 계산은 DATA.users로 모듈로한다 (lib/config.js:53,75).
// 둘이 어긋나면 토큰 주인 ≠ 크루가 되어 409가 아닌 403/404가 나고, verify_duplicate가 못 잡는다.
if (tokens.length < DATA.users) {
	throw new Error(
		`[load-write-heavy] tokens.csv(${tokens.length}개) < DATA.users(${DATA.users}). ` +
		`XXL 시딩으로 tokens.csv를 재생성하라.`
	);
}

const targetVUs = parseInt(__ENV.TARGET_VUS || '30');
// 기본값 15s = 유저풀 소진 방지. XXL(10k users) / 실측 포화 490TPS ≈ 20s가 상한이라
// 안전마진 25%를 둔 값. 근거·계산식은 아래 writePureExec 주석 참조.
const duration = __ENV.DURATION || '15s';

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
		// [신규] 유저풀 소진 감지 — 409가 1건이라도 나오면 이 런은 측정 무효다.
		// (DURATION이 유저풀 대비 과대할 때 발생. 위 writePureExec 주석의 계산식 참조)
		verify_duplicate: ['count==0'],
	},
};

/**
 * 매 iteration마다 iterationInTest를 유저풀 크기로 모듈로 → 고유 유저 분배.
 * 유저풀이 한 바퀴 돌면 그때부터 전부 409가 되어 측정이 무효가 된다.
 *
 * 안전 조건:  실측TPS × DURATION(초)  ≤  DATA.users × 0.8
 *
 * 실측 처리량 (Day 7, results/raw/day7_write-*.log):
 *   VU 10  → 309/s     VU 30~150 → 473~491/s (포화 평탄 구간)
 *
 * XXL(DATA.users=10,000) 기준 DURATION 상한:
 *   VU 30 이상 (490/s) → 10000×0.8/490 ≈ 16s  → 15s 사용
 *   VU 10      (309/s) → 10000×0.8/309 ≈ 25s  → 20s 이하 권장
 *   ※ Day 7의 VU10 런은 30s로 돌려 풀의 93%를 소진했다. 마진 없이 통과한 것이므로
 *     재현 시 그대로 따라하지 말 것.
 *
 * 더 긴 런이 필요하면 DURATION을 늘리지 말고 유저풀을 키운다(시딩 확대).
 * 런 사이 리셋은 sql/08_reset_api_verifications.sql (런 도중에는 리셋 불가).
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

export { handleSummary } from './lib/report.js';
