// B 테스트 응답 분류 (measurement-contract K4·K5) — 서로 배타적, 한 응답은 정확히 한 분류.
// 과거 lib/metrics.js의 join_full(409 전부를 정원 초과로 셈)은 쓰지 않는다: CR002만 join_cr002.
import { Counter, Trend } from 'k6/metrics';

// result 태그 → Counter 이름. 순서는 종료 요약 출력 순서.
export const COUNTER_BY_RESULT = {
	'201': 'join_201',
	cr002: 'join_cr002',
	cr004: 'join_cr004',
	other_4xx: 'join_other_4xx',
	'5xx': 'join_5xx',
	transport: 'join_transport_err',
	other: 'join_other',
};
export const RESULTS = Object.keys(COUNTER_BY_RESULT);

const counters = {};
for (const r of RESULTS) counters[r] = new Counter(COUNTER_BY_RESULT[r]);
const joinDuration = new Trend('join_duration', true);

// 오류 바디 {"success":false,"data":null,"error":{"code":"...",...}}의 error.code. 파싱 실패·빈 바디는 null.
function errorCode(res) {
	try {
		const b = res.json();
		return b && b.error ? b.error.code : null;
	} catch (_) {
		return null;
	}
}

// JSON 파싱은 409일 때만 한다 (201·그 외는 상태 코드만으로 분류).
export function classify(res) {
	const s = res.status;
	if (s === 201) return '201';
	if (s === 0) return 'transport'; // 연결 실패·리셋·타임아웃
	if (s === 409) {
		const code = errorCode(res);
		if (code === 'CR002') return 'cr002';
		if (code === 'CR004') return 'cr004';
		return 'other_4xx';
	}
	if (s >= 400 && s < 500) return 'other_4xx';
	if (s >= 500 && s < 600) return '5xx';
	return 'other';
}

// 분류해서 Counter 1 + join_duration{result} 1점을 기록하고 분류를 돌려준다.
export function record(res) {
	const r = classify(res);
	counters[r].add(1);
	joinDuration.add(res.timings.duration, { result: r });
	return r;
}
