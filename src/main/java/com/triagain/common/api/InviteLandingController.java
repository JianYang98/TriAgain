package com.triagain.common.api;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 크루 초대 링크 랜딩 페이지 컨트롤러 — 인증 불필요, DB 조회 없음 */
@Controller
public class InviteLandingController {

	/** 초대코드로 랜딩 페이지 반환 — 모바일 브라우저에서 앱 설치 및 코드 복사 유도 */
	@GetMapping("/invite/{inviteCode}")
	public String inviteLanding(
			@PathVariable String inviteCode,
			Model model) {
		model.addAttribute("inviteCode", inviteCode);
		return "invite";
	}
}
