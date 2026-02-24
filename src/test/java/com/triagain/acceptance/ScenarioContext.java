package com.triagain.acceptance;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope
public class ScenarioContext {

    private ExtractableResponse<Response> response;

    public void setResponse(ExtractableResponse<Response> response) {
        this.response = response;
    }

    public ExtractableResponse<Response> getResponse() {
        return response;
    }
}
