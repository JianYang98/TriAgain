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
}
