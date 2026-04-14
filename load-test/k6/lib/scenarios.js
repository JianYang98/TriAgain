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
export function crewRush() {
	const params = authHeaders(__VU);
	const rushCrewId = `loadtest-rush-crew-${(__ITER % RUSH_CREW_COUNT) + 1}`;

	const res = http.post(`${BASE_URL}/crews/${rushCrewId}/join`, null, params);
	scenarioDDuration.add(res.timings.duration);

	if (res.status === 201) {
		joinSuccess.add(1);
	} else if (res.status === 409) {
		joinFull.add(1);
	}

	check(res, {
		'D: join or full': (r) => r.status === 201 || r.status === 409,
	});
}
