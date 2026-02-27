package com.triagain.acceptance.steps;

import com.triagain.acceptance.ScenarioContext;
import com.triagain.acceptance.adapter.UploadSessionTestAdapter;
import com.triagain.verification.api.CreateUploadSessionRequest;
import io.cucumber.java.Before;
import io.cucumber.java.ko.만일;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

public class UploadSessionSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private ScenarioContext scenarioContext;

    private UploadSessionTestAdapter uploadSessionAdapter;

    @Before
    public void setUp() {
        uploadSessionAdapter = new UploadSessionTestAdapter(port);
    }

    @만일("파일명 {string}, 타입 {string}, 크기 {long}으로 업로드 세션 생성을 요청하면")
    public void 업로드_세션_생성을_요청하면(String fileName, String fileType, long fileSize) {
        CreateUploadSessionRequest request = new CreateUploadSessionRequest(fileName, fileType, fileSize);
        scenarioContext.setResponse(uploadSessionAdapter.createUploadSession(scenarioContext.getUserId(), request));
    }
}
