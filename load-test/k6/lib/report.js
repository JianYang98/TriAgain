// ============================================================
// k6 HTML Report — handleSummary wrapper
//
// 사용법: 각 k6 스크립트 맨 아래에 한 줄 추가
//   export { handleSummary } from './lib/report.js';
//
// 결과: results/raw/<스크립트명>_<timestamp>.html 자동 생성
// ============================================================

import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/2.4.0/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

export function handleSummary(data) {
	// 스크립트명에서 파일명 추출 (예: load-write-heavy.js → load-write-heavy)
	const scriptName = __ENV.K6_SCRIPT_NAME || 'k6-report';
	const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
	const htmlFile = `results/raw/${scriptName}_${timestamp}.html`;

	return {
		[htmlFile]: htmlReport(data),
		stdout: textSummary(data, { indent: '  ', enableColors: true }),
	};
}
