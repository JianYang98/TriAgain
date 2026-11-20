// -d 세트: db-baseline-c-capacity.js의 복제. 원본 대신 db-baseline-c-d.js의 옵션을 읽어 300·600 RPS도 막히지 않는다.
// 본 측정과 같은 C 모듈·토큰을 읽고 VU를 준비한다. 가입 API를 포함한 HTTP 요청은 보내지 않는다.
// 서버 연결·네트워크·실제 요청 중 CPU 사용량까지 검증하는 도구는 아니다.
import { sleep } from 'k6';
import { options as mainOptions } from './db-baseline-c-d.js';

const vus = mainOptions.scenarios.constant_rps_test.preAllocatedVUs;
export const options = {
	scenarios: {
		capacity: { executor: 'shared-iterations', vus, iterations: vus, maxDuration: '30s' },
	},
	thresholds: { iterations: [`count==${vus}`] },
};

export default function () {
	sleep(1);
}

export function handleSummary(data) {
	const requests = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
	const iterations = data.metrics.iterations ? data.metrics.iterations.values.count : 0;
	return { stdout: `\nC VU 준비 확인: VU=${vus}, 완료=${iterations}, HTTP=${requests}\n` +
		'HTTP=0인지와 맥 CPU·메모리 압력·스왑을 확인한다. 실제 부하 수용 여부는 별도 검증한다.\n' };
}
