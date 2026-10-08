package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class UploadSessionTestAdapter extends BaseTestAdapter {

	public UploadSessionTestAdapter(int port) {
		super(port);
	}

	/** 업로드 세션 생성 — POST /upload-sessions */
	public ExtractableResponse<Response> createUploadSession(String userId, Object request) {
		return givenAuthRequest(userId)
				.body(request)
				.when()
				.post("/upload-sessions")
				.then()
				.log().ifError()
				.extract();
	}

	/** Lambda 콜백 — PUT /internal/upload-sessions/complete?imageKey=... */
	public ExtractableResponse<Response> completeUploadSession(String imageKey) {
		return givenRequest()
				.queryParam("imageKey", imageKey)
				.when()
				.put("/internal/upload-sessions/complete")
				.then()
				.log().ifError()
				.extract();
	}

	/** 업로드 세션 상태 조회 — GET /upload-sessions/{id} */
	public ExtractableResponse<Response> getUploadSessionStatus(String userId, Long id) {
		return givenAuthRequest(userId)
				.when()
				.get("/upload-sessions/" + id)
				.then()
				.log().ifError()
				.extract();
	}

	/** 토큰 없이 업로드 세션 상태 조회 — 401 검증용 */
	public ExtractableResponse<Response> getUploadSessionStatusWithoutAuth(Long id) {
		return givenRequest()
				.when()
				.get("/upload-sessions/" + id)
				.then()
				.log().ifError()
				.extract();
	}
}
