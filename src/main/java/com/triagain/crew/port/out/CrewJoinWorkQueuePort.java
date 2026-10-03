package com.triagain.crew.port.out;

import java.time.Duration;

public interface CrewJoinWorkQueuePort {

	/** pending 오른쪽 작업을 processing 왼쪽으로 이동 — 정상 대기 만료만 null */
	String claimRaw(Duration timeout);

	/** DB commit 뒤 수신한 원문 한 건을 ACK — 삭제 개수를 반환 */
	long ackRaw(String raw);

	/** processing 왼쪽 한 건을 pending 오른쪽으로 원자 이동 — startup recovery 전용, null은 processing 소진 */
	String recoverOneRaw();
}
