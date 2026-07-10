package com.triagain.common.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 문의·건의 리다이렉트 컨트롤러 — 인증 불필요, DB 조회 없음 */
@Controller
public class FeedbackRedirectController {

	@Value("${triagain.feedback-form-url}")
	private String feedbackFormUrl;

	/** 문의·건의 구글폼으로 302 리다이렉트 — 앱은 고정 URL만 가리키고 목적지는 설정으로 교체 */
	@GetMapping("/feedback")
	public String feedback() {
		return "redirect:" + feedbackFormUrl;
	}
}
