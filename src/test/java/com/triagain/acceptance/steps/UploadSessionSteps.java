package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.UploadSessionTestAdapter;
import com.triagain.verification.api.CreateUploadSessionRequest;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만일;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

public class UploadSessionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private UploadSessionTestAdapter uploadSessionAdapter;
    private String userId;

    @Before
    public void setUp() {
        uploadSessionAdapter = new UploadSessionTestAdapter(port);
    }

    @조건("사용자 {string}이 로그인되어 있다")
    public void 사용자가_로그인되어_있다(String userId) {
        this.userId = userId;
    }

    @만일("파일명 {string}, 타입 {string}, 크기 {long}으로 업로드 세션 생성을 요청하면")
    public void 업로드_세션_생성을_요청하면(String fileName, String fileType, long fileSize) {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest(fileName, fileType, fileSize);
        scenarioContext.setResponse(uploadSessionAdapter.createUploadSession(userId, request));
    }

    @그리고("^응답에 (.+)[이가] 존재한다$")
    public void 응답에_필드가_존재한다(String field) {
        Object value = scenarioContext.getResponse().jsonPath().get("data." + field);
        assertThat(value).isNotNull();
    }
}
