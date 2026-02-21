package com.triagain.acceptance.adapter;

import io.restassured.RestAssured;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

public abstract class BaseTestAdapter {

    private final int port;

    protected BaseTestAdapter(int port) {
        this.port = port;
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected RequestSpecification givenRequest() {
        return RestAssured.given()
                .baseUri(baseUrl())
                .contentType("application/json")
                .log().ifValidationFails();
    }

    protected ExtractableResponse<Response> get(String path) {
        return givenRequest()
                .when()
                .get(path)
                .then()
                .log().ifError()
                .extract();
    }

    protected ExtractableResponse<Response> post(String path, Object body) {
        return givenRequest()
                .body(body)
                .when()
                .post(path)
                .then()
                .log().ifError()
                .extract();
    }

    protected ExtractableResponse<Response> put(String path, Object body) {
        return givenRequest()
                .body(body)
                .when()
                .put(path)
                .then()
                .log().ifError()
                .extract();
    }

    protected ExtractableResponse<Response> delete(String path) {
        return givenRequest()
                .when()
                .delete(path)
                .then()
                .log().ifError()
                .extract();
    }
}
