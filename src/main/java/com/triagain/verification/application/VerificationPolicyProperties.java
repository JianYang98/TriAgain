package com.triagain.verification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** 인증 취소·수정 정책 설정 — yml로 취소 컷오프·슬롯 상한 조정 */
@Getter
@Setter
@ConfigurationProperties(prefix = "triagain.verification")
public class VerificationPolicyProperties {

	private Integer cancelCutoffMinutes;

	private Integer slotAttemptLimit;

	/** 업로드 세션 SSE 구독의 서버측 타임아웃(ms) — 기본 60000 유지, 테스트에서만 단축 오버라이드 */
	private Long sseTimeoutMs;
}
