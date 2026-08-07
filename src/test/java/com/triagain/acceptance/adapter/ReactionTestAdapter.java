package com.triagain.acceptance.adapter;

import java.util.Map;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class ReactionTestAdapter extends BaseTestAdapter {

	public ReactionTestAdapter(int port) {
		super(port);
	}

	/** 인증에 리액션 추가 — PUT /verifications/{verificationId}/reactions */
	public ExtractableResponse<Response> addReaction(String userId, String verificationId, String emojiType) {
		return givenAuthRequest(userId)
				.body(Map.of("emojiType", emojiType))
				.when()
				.put("/verifications/" + verificationId + "/reactions")
				.then()
				.log().ifError()
				.extract();
	}

	/** 인증에 남긴 내 리액션 제거 — DELETE /verifications/{verificationId}/reactions */
	public ExtractableResponse<Response> removeReaction(String userId, String verificationId) {
		return givenAuthRequest(userId)
				.when()
				.delete("/verifications/" + verificationId + "/reactions")
				.then()
				.log().ifError()
				.extract();
	}
}
