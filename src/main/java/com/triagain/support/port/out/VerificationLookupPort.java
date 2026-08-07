package com.triagain.support.port.out;

import java.util.Optional;

public interface VerificationLookupPort {

	/** 인증의 크루 id 조회 — status 무관(CANCELLED 포함 전부 조회, E1-d의 DELETE 허용이 이 특성에 기댄다) */
	Optional<String> findCrewIdById(String verificationId);
}
