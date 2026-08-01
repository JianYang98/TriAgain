package com.triagain.e2e;

import com.triagain.crew.domain.model.Challenge;
import com.triagain.crew.domain.model.Crew;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** E2E 해피패스 테스트 — 핵심 유저 플로우 5개 검증 */
class HappyPathE2eTest extends E2eTestBase {

	@Test
	@DisplayName("회원가입 + 프로필 조회 해피패스")
	void signupAndProfile() {
		// Given: DB에 유저 생성 (카카오 로그인 완료 상태 시뮬레이션)
		String userId = "e2e-user-001";
		createUser(userId);

		// When: 내 프로필 조회
		ExtractableResponse<Response> response = authGet(userId, "/users/me");

		// Then: 유저 정보 정상 응답
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.jsonPath().getBoolean("success")).isTrue();
		assertThat(response.jsonPath().getString("data.id")).isEqualTo(userId);
		assertThat(response.jsonPath().getString("data.nickname")).isEqualTo(userId);
		assertThat(response.jsonPath().getString("data.email")).isEqualTo(userId + "@test.com");
	}

	@Test
	@DisplayName("크루 생성 해피패스")
	void createCrew() {
		// Given: 유저 생성
		String userId = "e2e-user-002";
		createUser(userId);

		// When: 크루 생성 API 호출
		Map<String, Object> request = Map.of(
				"name", "E2E 테스트 크루",
				"goal", "매일 운동하기",
				"verificationContent", "운동 인증샷",
				"verificationType", "TEXT",
				"maxMembers", 10,
				"startDate", LocalDate.now().plusDays(1).toString(),
				"endDate", LocalDate.now().plusDays(15).toString(),
				"allowLateJoin", true,
				"category", "EXERCISE"
		);
		ExtractableResponse<Response> response = authPost(userId, "/crews", request);

		// Then: 201 + 크루 정보 확인
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.jsonPath().getBoolean("success")).isTrue();
		assertThat(response.jsonPath().getString("data.crewId")).isNotBlank();
		assertThat(response.jsonPath().getString("data.inviteCode")).isNotBlank();
		assertThat(response.jsonPath().getString("data.status")).isEqualTo("RECRUITING");
		assertThat(response.jsonPath().getString("data.name")).isEqualTo("E2E 테스트 크루");
		assertThat(response.jsonPath().getInt("data.currentMembers")).isEqualTo(1);
	}

	@Test
	@DisplayName("크루 참여 해피패스")
	void joinCrew() {
		// Given: 유저A가 크루 생성
		String userA = "e2e-user-A";
		String userB = "e2e-user-B";
		createUser(userA);
		createUser(userB);

		Map<String, Object> crewRequest = Map.of(
				"name", "참여 테스트 크루",
				"goal", "함께 공부하기",
				"verificationContent", "공부 인증",
				"verificationType", "TEXT",
				"maxMembers", 10,
				"startDate", LocalDate.now().plusDays(1).toString(),
				"endDate", LocalDate.now().plusDays(15).toString(),
				"allowLateJoin", true,
				"category", "EXERCISE"
		);
		ExtractableResponse<Response> createResponse = authPost(userA, "/crews", crewRequest);
		String crewId = createResponse.jsonPath().getString("data.crewId");
		String inviteCode = createResponse.jsonPath().getString("data.inviteCode");

		// When: 유저B가 초대코드로 참여
		Map<String, String> joinRequest = Map.of("inviteCode", inviteCode);
		ExtractableResponse<Response> joinResponse = authPost(userB, "/crews/join", joinRequest);

		// Then: 201 + MEMBER 역할
		assertThat(joinResponse.statusCode()).isEqualTo(201);
		assertThat(joinResponse.jsonPath().getString("data.role")).isEqualTo("MEMBER");

		// Then: 크루 상세 조회 시 currentMembers=2
		ExtractableResponse<Response> detailResponse = authGet(userA, "/crews/" + crewId);
		assertThat(detailResponse.jsonPath().getInt("data.currentMembers")).isEqualTo(2);
	}

	@Test
	@DisplayName("텍스트 인증 제출 해피패스")
	void textVerification() {
		// Given: ACTIVE 크루 + IN_PROGRESS 챌린지 (DB 직접 세팅)
		String userId = "e2e-user-003";
		createUser(userId);
		Crew crew = createActiveCrew(userId);
		Challenge challenge = createChallenge(userId, crew.getId(), 0);

		// When: 텍스트 인증 제출
		Map<String, String> request = Map.of(
				"crewId", crew.getId(),
				"textContent", "오늘 운동 완료!"
		);
		ExtractableResponse<Response> response = authPost(userId, "/verifications", request);

		// Then: 201 + APPROVED
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.jsonPath().getString("data.status")).isEqualTo("APPROVED");
		assertThat(response.jsonPath().getString("data.textContent")).isEqualTo("오늘 운동 완료!");

		// Then: 챌린지 completedDays=1 확인 (my-verifications API)
		ExtractableResponse<Response> myVerifications = authGet(userId,
				"/crews/" + crew.getId() + "/my-verifications");
		assertThat(myVerifications.jsonPath().getInt("data.myProgress.completedDays")).isEqualTo(1);
		assertThat(myVerifications.jsonPath().getString("data.myProgress.status")).isEqualTo("IN_PROGRESS");
	}

	@Test
	@DisplayName("3일 연속 인증 → 챌린지 SUCCESS 해피패스")
	void threeDaySuccessChallenge() {
		// Given: ACTIVE 크루 + completedDays=2 챌린지 (DB 직접 세팅)
		String userId = "e2e-user-004";
		createUser(userId);
		Crew crew = createActiveCrew(userId);
		createChallenge(userId, crew.getId(), 2);

		// When: 마지막 3일차 인증 제출
		Map<String, String> request = Map.of(
				"crewId", crew.getId(),
				"textContent", "3일차 인증 완료!"
		);
		ExtractableResponse<Response> response = authPost(userId, "/verifications", request);

		// Then: 201 + 인증 생성 성공
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.jsonPath().getString("data.status")).isEqualTo("APPROVED");

		// Then: 챌린지 SUCCESS 확인 (my-verifications API)
		ExtractableResponse<Response> myVerifications = authGet(userId,
				"/crews/" + crew.getId() + "/my-verifications");
		assertThat(myVerifications.jsonPath().getInt("data.completedChallenges")).isEqualTo(1);
		assertThat((Object) myVerifications.jsonPath().get("data.myProgress")).isNull();
	}
}
