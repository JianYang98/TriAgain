package com.triagain.user.port.in;

/** 회원탈퇴 — 개인정보 초기화 + 크루 멤버십 정리 + 토큰 무효화 */
public interface WithdrawUserUseCase {

	void withdraw(String userId);
}
