import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
	BASE_URL, tokens, getUserCrewId, getCrewIdByUserIndex,
	authHeaders, authHeadersByIndex, RUSH_CREW_COUNT,
} from './config.js';
import {
	scenarioADuration, scenarioBDuration, scenarioDDuration,
	verifyCreated, verifyDuplicate, joinSuccess, joinFull,
	joinConflict, joinDup, joinError5xx, joinDropped,
	dupJoinSuccess, dupJoinAlreadyJoined, scenarioEDuration,
} from './metrics.js';

// ============================================================
// Scenario A: crew home (read)
// ============================================================
export function crewHome() {
	const params = authHeaders(__VU);
	const crewId = getUserCrewId(__VU);

	const res = http.get(`${BASE_URL}/crews/${crewId}`, params);
	scenarioADuration.add(res.timings.duration);

	check(res, { 'A: status 200': (r) => r.status === 200 });
}

// ============================================================
// Scenario B: verification write
//
// ramping-arrival-rate executor calls this function.
// Each iteration uses a DIFFERENT user (via iterationInTest)
// so that POST /verifications creates a real DB write (201)
// until all users are exhausted, then 409.
// ============================================================
export function writeScenario() {
	const userIndex = exec.scenario.iterationInTest % tokens.length;
	const params = authHeadersByIndex(userIndex);
	const crewId = getCrewIdByUserIndex(userIndex);

	// 1. Crew home
	const homeRes = http.get(`${BASE_URL}/crews/${crewId}`, params);
	scenarioBDuration.add(homeRes.timings.duration);

	sleep(1); // user think time

	// 2. POST /verifications (TEXT)
	const payload = JSON.stringify({
		crewId: crewId,
		textContent: `k6 load test iter-${exec.scenario.iterationInTest}`,
	});
	const verifyRes = http.post(`${BASE_URL}/verifications`, payload, params);
	scenarioBDuration.add(verifyRes.timings.duration);

	if (verifyRes.status === 201) {
		verifyCreated.add(1);
	} else if (verifyRes.status === 409) {
		verifyDuplicate.add(1);
	}

	check(verifyRes, {
		'B: created or duplicate': (r) => r.status === 201 || r.status === 409,
	});
}

// ============================================================
// Scenario D: crew join rush (concurrency test)
//
// 50 VUs simultaneously try to join a crew with max_members=10.
// Iterates over rush crews so multiple rounds are tested.
// ============================================================

// 응답 바디 error.code 안전 추출 (status 0/빈 바디/파싱 실패 시 null)
function errorCode(res) {
	try { const b = res.json(); return b && b.error ? b.error.code : null; }
	catch (_) { return null; }
}

export function crewRush() {
	const params = authHeaders(__VU);
	const rushCrewId = `loadtest-rush-crew-${(__ITER % RUSH_CREW_COUNT) + 1}`;

	const res = http.post(`${BASE_URL}/crews/${rushCrewId}/join`, null, params);
	scenarioDDuration.add(res.timings.duration);

	if (res.status === 201) {
		joinSuccess.add(1);
	} else if (res.status === 409) {
		const code = errorCode(res);
		if (code === 'CR004')      joinDup.add(1);
		else if (code === 'CR023') joinConflict.add(1);
		else                       joinFull.add(1);
	} else if (res.status >= 500) {
		joinError5xx.add(1);
	} else if (res.status === 0) {
		joinDropped.add(1);
	}

	check(res, { 'D: join or full': (r) => r.status === 201 || r.status === 409 });
}

// ============================================================
// Scenario E: duplicate join (전략 C 중복 가입 동시성 검증)
//
// 같은 토큰(VU 1)이 N발 동시 가입 시도.
// 기대: 멤버십 1건 + 나머지 409 CREW_ALREADY_JOINED.
// 사전 준비: DUP_CREW_ID 크루 + 단일 토큰(DUP_TOKEN) 준비 필요.
//
// k6 run --env BASE_URL=... --env DUP_CREW_ID=... --env DUP_TOKEN=... dup-join.js
// ============================================================
export function dupJoin() {
	const crewId = __ENV.DUP_CREW_ID;
	const token = __ENV.DUP_TOKEN;
	const params = {
		headers: {
			'Authorization': `Bearer ${token}`,
			'Content-Type': 'application/json',
		},
	};

	const res = http.post(`${BASE_URL}/crews/${crewId}/join`, null, params);
	scenarioEDuration.add(res.timings.duration);

	if (res.status === 201) {
		dupJoinSuccess.add(1);
	} else if (res.status === 409) {
		dupJoinAlreadyJoined.add(1);
	}

	check(res, {
		'E: first join or already-joined': (r) => r.status === 201 || r.status === 409,
	});
}
