package com.triagain.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FeedbackRedirectControllerTest {

	@Test
	@DisplayName("GET /feedback 요청하면 설정된 구글폼 URL로 302 리다이렉트한다")
	void feedback_요청하면_설정된_폼_URL로_302_리다이렉트한다() throws Exception {
		// Given: 설정값으로 폼 URL이 주입된 컨트롤러
		String formUrl = "https://docs.google.com/forms/d/e/test-form/viewform";
		FeedbackRedirectController controller = new FeedbackRedirectController();
		ReflectionTestUtils.setField(controller, "feedbackFormUrl", formUrl);
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

		// When & Then: GET /feedback → 302 + Location이 설정값과 일치
		mockMvc.perform(get("/feedback"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(formUrl));
	}
}
