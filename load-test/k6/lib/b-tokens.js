// B 테스트 토큰 로딩 — generate-tokens.sh 출력 그대로 (헤더 `token` + 토큰 1열, UTF-8, LF).
// 행 순서 = 사용자 번호(loadtest-user-1…N). 되감기 없이 iterationInTest 인덱스로만 고른다.
// 토큰 값은 어떤 메시지·로그에도 쓰지 않는다 (오류 메시지에는 개수·행 번호만).
import { SharedArray } from 'k6/data';

// text → 앞 total개 토큰 배열. warmup=false(본 측정)는 행 수 == total, true(워밍업)는 행 수 >= total.
export function parseTokens(text, total, warmup) {
	const lines = text.split('\n');
	if (lines[lines.length - 1] === '') lines.pop(); // 파일 끝 개행 하나만 허용
	if (lines.length === 0 || lines[0].trim() !== 'token') {
		throw new Error('토큰 파일 헤더가 "token"이 아니다');
	}
	const rows = lines.slice(1).map((l) => l.trim());
	const seen = new Set();
	rows.forEach((t, i) => {
		if (t === '') throw new Error(`토큰 파일 ${i + 2}번째 줄이 비어 있다`);
		if (seen.has(t)) throw new Error(`토큰 파일 ${i + 2}번째 줄이 앞 행과 중복이다`);
		seen.add(t);
	});
	if (warmup ? rows.length < total : rows.length !== total) {
		throw new Error(`토큰 행 수 ${rows.length} ${warmup ? '<' : '!='} TOTAL ${total} (WARMUP=${warmup ? 1 : 0})`);
	}
	return rows.slice(0, total);
}

// path는 절대 경로 (k6 open()의 상대 경로 기준은 cwd가 아니라 스크립트 위치라 혼동을 막는다).
export function loadTokens(path, total, warmup) {
	return new SharedArray('b-tokens', () => parseTokens(open(path), total, warmup));
}
