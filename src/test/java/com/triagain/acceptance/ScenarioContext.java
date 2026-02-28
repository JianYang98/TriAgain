package com.triagain.acceptance;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ScenarioScope
public class ScenarioContext {

    private ExtractableResponse<Response> response;
    private String userId;
    private String crewId;
    private String inviteCode;
    private String creatorId;
    private String challengeId;
    private Long uploadSessionId;
    private final Map<String, String> crewNameToId = new HashMap<>();

    public void setResponse(ExtractableResponse<Response> response) {
        this.response = response;
    }

    public ExtractableResponse<Response> getResponse() {
        return response;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCrewId() {
        return crewId;
    }

    public void setCrewId(String crewId) {
        this.crewId = crewId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public void putCrewId(String crewName, String crewId) {
        this.crewNameToId.put(crewName, crewId);
    }

    public String getCrewIdByName(String crewName) {
        return this.crewNameToId.get(crewName);
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public Long getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(Long uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }
}
