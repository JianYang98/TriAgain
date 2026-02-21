package com.triagain.acceptance.adapter;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

public class HealthTestAdapter extends BaseTestAdapter {

    public HealthTestAdapter(int port) {
        super(port);
    }

    public ExtractableResponse<Response> getHealth() {
        return get("/health");
    }
}
