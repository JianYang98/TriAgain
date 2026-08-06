package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class VerificationTestAdapter extends BaseTestAdapter {

	public VerificationTestAdapter(int port) {
		super(port);
	}

	/** 인증 생성 — POST /verifications */
	public ExtractableResponse<Response> createVerification(String userId, Object request) {
		return givenAuthRequest(userId)
				.body(request)
				.when()
				.post("/verifications")
				.then()
				.log().ifError()
				.extract();
	}

	/** 인증 취소 — DELETE /verifications/{verificationId} */
	public ExtractableResponse<Response> cancelVerification(String userId, String verificationId) {
		return givenAuthRequest(userId)
				.when()
				.delete("/verifications/" + verificationId)
				.then()
				.log().ifError()
				.extract();
	}

	/** 인증 수정 — PATCH /verifications/{verificationId} */
	public ExtractableResponse<Response> updateVerification(String userId, String verificationId, Object request) {
		return givenAuthRequest(userId)
				.body(request)
				.when()
				.patch("/verifications/" + verificationId)
				.then()
				.log().ifError()
				.extract();
	}
}
