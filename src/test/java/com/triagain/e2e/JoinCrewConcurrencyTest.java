package com.triagain.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewStatus;
import com.triagain.crew.domain.vo.CrewVisibility;
import com.triagain.crew.domain.vo.VerificationType;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * CONDITIONAL 전략 실DB 동시성 테스트 — Tier 3 머지 안전선.
 * flush 수정 없이 T2는 RED(중복행 생성), 수정 후 GREEN(CREW_ALREADY_JOINED).
 * 이 전이가 saveMemberAndFlush의 유일한 실증이다.
 */
class JoinCrewConcurrencyTest extends E2eTestBase {

	@PersistenceContext
	private EntityManager entityManager;

	@DynamicPropertySource
	static void overrideLockStrategy(DynamicPropertyRegistry registry) {
		// 부모(E2eTestBase)의 DB 설정을 그대로 상속하고, 전략만 CONDITIONAL로 강제
		registry.add("triagain.crew.lock-strategy", () -> "CONDITIONAL");
	}

	@Test
	@DisplayName("T1 정원 — 정원 5 크루에 30스레드 동시 가입 시 정확히 5명만 성공한다")
	void t1_capacityRace_onlyMaxMembersSucceed() throws Exception {
		// Given: 정원 5 PUBLIC 크루, 리더 1명 이미 반영(currentMembers=1) — 빈 자리 4
		int maxMembers = 5;
		int threads = 30;
		String leaderId = "t1-leader-" + System.nanoTime();
		String crewId = IdGenerator.generate("CREW");
		createUser(leaderId);

		// currentMembers=1로 저장 (리더 1명 이미 반영) — incrementMembersIfNotFull이 4회만 허용
		Crew crew = buildPublicRecruitingCrew(crewId, leaderId, maxMembers, 1);
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(leaderId, crewId));

		List<String> userIds = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			String uid = "t1-user-" + i + "-" + System.nanoTime();
			createUser(uid);
			userIds.add(uid);
		}

		// When: 30스레드 동시 가입 (crewId 직접 가입 API)
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<Future<ExtractableResponse<Response>>> futures = new ArrayList<>();

		for (String uid : userIds) {
			futures.add(executor.submit(() -> {
				ready.countDown();
				start.await();
				return authPost(uid, "/crews/" + crewId + "/join", Map.of());
			}));
		}

		ready.await();
		start.countDown();

		AtomicInteger successCount = new AtomicInteger(0);
		List<String> failErrorCodes = new ArrayList<>();
		for (Future<ExtractableResponse<Response>> f : futures) {
			ExtractableResponse<Response> res = f.get();
			int status = res.statusCode();
			if (status == 201) {
				successCount.incrementAndGet();
			} else {
				String code = res.jsonPath().getString("error.code");
				if (code != null) {
					failErrorCodes.add(code);
				}
			}
		}
		executor.shutdown();

		// Then: 정확히 (maxMembers-1)명 성공 (리더 1명 이미 있음, API 통해 추가 4명)
		int expectedNewJoins = maxMembers - 1;
		assertThat(successCount.get()).isEqualTo(expectedNewJoins);

		// 거부 건수: (threads - expectedNewJoins)건이 CREW_FULL(CR002)이어야 함
		// 스냅샷 full을 CR003(400)으로 자르면 이 단언이 깨진다 — 버그 검출 포인트
		int expectedRejections = threads - expectedNewJoins;
		assertThat(failErrorCodes)
			.withFailMessage(
				"정원 거부 %d건이 전부 CR002(CREW_FULL)이어야 함. 실제: %s."
					+ " CR003이 섞이면 스냅샷 full을 CREW_NOT_RECRUITING으로 오분류한 것 "
					+ "→ addMemberSkipCapacityCheck가 canNotJoin(isFull 포함)을 쓰는 버그",
				expectedRejections, failErrorCodes)
			.hasSize(expectedRejections)
			.allMatch(code -> "CR002".equals(code));

		// DB 직접 검증: current_members와 crew_members 행 수가 maxMembers와 일치
		Long memberCount = (Long) entityManager
			.createNativeQuery(
				"SELECT COUNT(*) FROM crew_members WHERE crew_id = :crewId")
			.setParameter("crewId", crewId)
			.getSingleResult();
		assertThat(memberCount.intValue()).isEqualTo(maxMembers);

		List<?> currentMembersList = entityManager
			.createNativeQuery(
				"SELECT current_members FROM crews WHERE id = :crewId")
			.setParameter("crewId", crewId)
			.getResultList();
		int currentMembers = ((Number) currentMembersList.get(0)).intValue();
		assertThat(currentMembers).isEqualTo(maxMembers);
	}

	@Test
	@DisplayName("T2 중복 — 같은 유저 2스레드 동시 가입 시 1건만 성공하고 CREW_ALREADY_JOINED 1건이다")
	void t2_duplicateJoinConcurrent_onlyOneSucceeds() throws Exception {
		// Given: PUBLIC 크루 (정원 10, 리더 외 자리 충분)
		String leaderId = "t2-leader-" + System.nanoTime();
		String sameUserId = "t2-dup-user-" + System.nanoTime();
		String crewId = IdGenerator.generate("CREW");
		createUser(leaderId);
		createUser(sameUserId);

		Crew crew = buildPublicRecruitingCrew(crewId, leaderId, 10, 1);
		crewRepositoryPort.save(crew);
		crewRepositoryPort.saveMember(CrewMember.createLeader(leaderId, crewId));

		// When: 같은 유저 2스레드 동시 가입
		int threads = 2;
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		List<Future<ExtractableResponse<Response>>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			futures.add(executor.submit(() -> {
				ready.countDown();
				start.await();
				return authPost(sameUserId, "/crews/" + crewId + "/join", Map.of());
			}));
		}

		ready.await();
		start.countDown();

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger alreadyJoinedCount = new AtomicInteger(0);
		List<String> errorCodes = new ArrayList<>();
		for (Future<ExtractableResponse<Response>> f : futures) {
			ExtractableResponse<Response> res = f.get();
			int status = res.statusCode();
			if (status == 201) {
				successCount.incrementAndGet();
			} else if (status == 409) {
				alreadyJoinedCount.incrementAndGet();
				String code = res.jsonPath().getString("error.code");
				if (code != null) {
					errorCodes.add(code);
				}
			}
		}
		executor.shutdown();

		// Then: 정확히 성공 1건 / 실패(409) 1건
		assertThat(successCount.get()).isEqualTo(1);
		assertThat(alreadyJoinedCount.get())
			.withFailMessage("중복 가입 거부가 1건이어야 하지만 %d건", alreadyJoinedCount.get())
			.isEqualTo(1);

		// saveMemberAndFlush 검증 — 에러코드 CR004(CREW_ALREADY_JOINED)이어야 함.
		// plain save면 커밋 시점에 DataIntegrityViolationException이 GlobalExceptionHandler로 빠져 C004(DATA_CONFLICT) 반환.
		assertThat(errorCodes)
			.withFailMessage("에러코드가 CR004(CREW_ALREADY_JOINED)이어야 함. 실제: %s. "
				+ "plain save면 C004(DATA_CONFLICT)가 반환됨 → saveMemberAndFlush 미적용 상태", errorCodes)
			.allMatch(code -> "CR004".equals(code));

		// DB 직접 검증: sameUserId의 멤버십 정확히 1행 (유니크 제약 작동 확인)
		Long membershipCount = (Long) entityManager
			.createNativeQuery(
				"SELECT COUNT(*) FROM crew_members WHERE crew_id = :crewId AND user_id = :userId")
			.setParameter("crewId", crewId)
			.setParameter("userId", sameUserId)
			.getSingleResult();
		assertThat(membershipCount.intValue())
			.withFailMessage("멤버십 행이 1이어야 하지만 %d건 (유니크 제약 미작동)", membershipCount.intValue())
			.isEqualTo(1);
	}

	private Crew buildPublicRecruitingCrew(String crewId, String leaderId,
			int maxMembers, int currentMembers) {
		return Crew.of(
			crewId, leaderId, "동시성 테스트 크루", "동시성 목표",
			"인증 내용", VerificationType.TEXT, maxMembers, currentMembers,
			CrewStatus.RECRUITING,
			LocalDate.now().plusDays(1), LocalDate.now().plusDays(30),
			true, generateCode(), LocalDateTime.now(),
			Crew.DEFAULT_DEADLINE_TIME, null, CrewVisibility.PUBLIC, 0L, List.of()
		);
	}

	private String generateCode() {
		String chars = Crew.INVITE_CODE_CHARS;
		StringBuilder code = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			code.append(chars.charAt((int) (Math.random() * chars.length())));
		}
		return code.toString();
	}
}
