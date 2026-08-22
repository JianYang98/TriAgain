package com.triagain.verification.application;

import org.springframework.stereotype.Service;

import com.triagain.verification.port.in.SubscribeUploadSessionUseCase;
import com.triagain.verification.port.in.UploadSessionQueryUseCase;
import com.triagain.verification.port.out.SsePort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscribeUploadSessionService implements SubscribeUploadSessionUseCase {

	private final UploadSessionQueryUseCase uploadSessionQueryUseCase;
	private final SsePort ssePort;

	/** 업로드 세션 SSE 구독 — 소유권 검증 후 emitter 를 등록하고, 등록 사이에 끼어든 완료를 재조회로 줍는다 */
	@Override
	public Object subscribe(Long uploadSessionId, String userId) {
		// 1) 소유권 검증 — 반드시 등록보다 먼저. 무단 사용자는 emitter 자체를 못 만든다
		uploadSessionQueryUseCase.getOwnedOrThrow(uploadSessionId, userId);

		// 2) emitter 등록 — 이 시점 이후 도착하는 콜백은 전부 배달된다
		Object emitter = ssePort.subscribe(uploadSessionId);

		// 3) 등록 '이후' 재조회 — 1)과 2) 사이에 끼어든 완료 콜백을 여기서 줍는다.
		//    1)의 스냅샷을 재사용하면 그 창이 그대로 열린다
		if (uploadSessionQueryUseCase.getOwnedOrThrow(uploadSessionId, userId).completed()) {
			ssePort.send(uploadSessionId, "COMPLETED");
		}
		return emitter;
	}
}
