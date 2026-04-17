import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// === ENV ===
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const SCALE = __ENV.SCALE || 'S';

// === Scale -> data range ===
const SCALE_MAP = {
	S:   { users: 50,    crews: 10   },
	M:   { users: 250,   crews: 50   },
	L:   { users: 1000,  crews: 200  },
	XL:  { users: 2500,  crews: 500  },
	XXL: { users: 10000, crews: 2000 },
};
export const DATA = SCALE_MAP[SCALE];

// === Rush crew config ===
export const RUSH_CREW_COUNT = 10;

// === Tokens (SharedArray — init once, shared across VUs) ===
export const tokens = new SharedArray('tokens', function () {
	const csv = open('../../tokens.csv');
	const parsed = papaparse.parse(csv, { header: true }).data;
	return parsed.filter(row => row.token && row.token.trim()).map(row => row.token.trim());
});

// === Helpers ===

/**
 * VU -> user index (1-based).
 * VU 1 -> user 1, VU 51 -> user 1 (wraps).
 */
export function vuToUserIndex(vu) {
	return ((vu - 1) % DATA.users) + 1;
}

/**
 * User index -> crew ID the user belongs to.
 * SQL 03_crew_members.sql round-robin: user 1~5 -> crew 1, 6~10 -> crew 2, ...
 */
export function getUserCrewId(vu) {
	const userIndex = vuToUserIndex(vu);
	const crewIndex = ((Math.ceil(userIndex / 5) - 1) % DATA.crews) + 1;
	return `loadtest-crew-${crewIndex}`;
}

/**
 * Arbitrary user index (0-based) -> crew ID.
 * Used by writeScenario where iterationInTest picks different users.
 */
export function getCrewIdByUserIndex(userIndex0) {
	const userIndex = (userIndex0 % DATA.users) + 1;
	const crewIndex = ((Math.ceil(userIndex / 5) - 1) % DATA.crews) + 1;
	return `loadtest-crew-${crewIndex}`;
}

/**
 * Auth headers for a given VU.
 */
export function authHeaders(vu) {
	const token = tokens[(vu - 1) % tokens.length];
	return {
		headers: {
			Authorization: `Bearer ${token}`,
			'Content-Type': 'application/json',
		},
	};
}

/**
 * Auth headers for an arbitrary user index (0-based).
 */
export function authHeadersByIndex(userIndex0) {
	const token = tokens[userIndex0 % tokens.length];
	return {
		headers: {
			Authorization: `Bearer ${token}`,
			'Content-Type': 'application/json',
		},
	};
}
