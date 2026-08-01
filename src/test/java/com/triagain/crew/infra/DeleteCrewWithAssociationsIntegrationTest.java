package com.triagain.crew.infra;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.triagain.common.util.IdGenerator;
import com.triagain.crew.domain.model.Crew;
import com.triagain.crew.port.out.CrewRepositoryPort;
import com.triagain.e2e.E2eTestBase;
import com.triagain.support.domain.model.Notification;
import com.triagain.support.domain.vo.NotificationTargetType;
import com.triagain.support.domain.vo.NotificationType;
import com.triagain.support.port.out.NotificationRepositoryPort;
import com.triagain.verification.domain.model.UploadSession;
import com.triagain.verification.port.out.UploadSessionRepositoryPort;

/**
 * FK-safe 크루 삭제 고아 레코드 0건 통합 테스트 — 실제 PostgreSQL(Testcontainers) 사용.
 * <p>
 * 목적: {@link CrewJpaAdapter#deleteCrewWithAssociations(String)} 내부의
 * 네이티브 SQL 9-step 삭제가 실제 DB에서 테이블명/컬럼명/순서 오류 없이 동작하는지 검증한다.
 * Mock으로는 잡을 수 없는 SQL 오타·순서 오류·누락을 실제 PostgreSQL로 검출한다.
 * <p>
 * 인프라: E2eTestBase 재사용 (@SpringBootTest + @ActiveProfiles("integration") +
 * Testcontainers PostgreSQL 16 + create-drop DDL).
 * <p>
 * 검증 자식 테이블 목록:
 * - crew_members : 행 0건 (DELETE)
 * - notifications : target_type='CREW' AND target_id=crewId 행 0건 (DELETE)
 * - upload_session : 행이 남되 crew_id IS NULL (SET NULL)
 * - crews : 행 0건 (최종 삭제)
 */
@Tag("e2e")
class DeleteCrewWithAssociationsIntegrationTest extends E2eTestBase {

	@Autowired
	private CrewRepositoryPort crewRepositoryPort;

	@Autowired
	private NotificationRepositoryPort notificationRepositoryPort;

	@Autowired
	private UploadSessionRepositoryPort uploadSessionRepositoryPort;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("ACTIVE 솔로 크루 삭제 후 — crew_members/notifications(CREW)/crews 0건, upload_session crew_id IS NULL")
	@Transactional
	void deleteCrewWithAssociations_orphanRecordsAreZero() {
		// Given — 유저 + ACTIVE 솔로 크루 생성
		String userId = "integ-del-user-01";
		createUser(userId);
		Crew crew = createActiveCrew(userId);
		String crewId = crew.getId();

		// Given — CREW 타겟 알림 삽입
		Notification crewNotification = Notification.create(
				userId,
				NotificationType.CREW_STARTED,
				"크루 시작",
				"크루가 시작되었습니다.",
				NotificationTargetType.CREW,
				crewId
		);
		notificationRepositoryPort.save(crewNotification);

		// Given — upload_session (crew_id 연결) 삽입
		UploadSession session = UploadSession.create(
				userId, crewId,
				"uploads/" + userId + "/integ-test.jpg",
				"image/jpeg"
		);
		uploadSessionRepositoryPort.save(session);

		// 삽입 확인 (사전 조건)
		long membersBefore = countQuery("SELECT COUNT(*) FROM crew_members WHERE crew_id = :id", crewId);
		long crewsBefore = countQuery("SELECT COUNT(*) FROM crews WHERE id = :id", crewId);
		long notifsBefore = countQuery(
				"SELECT COUNT(*) FROM notifications WHERE target_type = 'CREW' AND target_id = :id", crewId);
		long sessionsBefore = countQuery("SELECT COUNT(*) FROM upload_session WHERE crew_id = :id", crewId);

		assertThat(membersBefore).as("사전 조건: crew_members 1건").isEqualTo(1);
		assertThat(crewsBefore).as("사전 조건: crews 1건").isEqualTo(1);
		assertThat(notifsBefore).as("사전 조건: notifications(CREW) 1건").isEqualTo(1);
		assertThat(sessionsBefore).as("사전 조건: upload_session 1건").isEqualTo(1);

		entityManager.flush();

		// When — FK-safe 삭제 실행
		crewRepositoryPort.deleteCrewWithAssociations(crewId);
		entityManager.flush();
		entityManager.clear();

		// Then — crew_members 0건
		long membersAfter = countQuery("SELECT COUNT(*) FROM crew_members WHERE crew_id = :id", crewId);
		assertThat(membersAfter).as("crew_members 고아 레코드 0건").isZero();

		// Then — crews 0건
		long crewsAfter = countQuery("SELECT COUNT(*) FROM crews WHERE id = :id", crewId);
		assertThat(crewsAfter).as("crews 삭제 확인 0건").isZero();

		// Then — notifications(target_type='CREW') 0건
		long notifsAfter = countQuery(
				"SELECT COUNT(*) FROM notifications WHERE target_type = 'CREW' AND target_id = :id", crewId);
		assertThat(notifsAfter).as("notifications(CREW) 고아 레코드 0건").isZero();

		// Then — upload_session 행은 남되 crew_id IS NULL (SET NULL)
		long sessionsWithCrewId = countQuery(
				"SELECT COUNT(*) FROM upload_session WHERE crew_id = :id", crewId);
		assertThat(sessionsWithCrewId).as("upload_session crew_id는 NULL 처리(행 자체는 남음)").isZero();

		long sessionsExist = countQueryNoParam(
				"SELECT COUNT(*) FROM upload_session WHERE user_id = 'integ-del-user-01'");
		assertThat(sessionsExist).as("upload_session 행 자체는 남아 있어야 함").isEqualTo(1);

		Long crewIdInSession = (Long) entityManager
				.createNativeQuery("SELECT crew_id FROM upload_session WHERE user_id = :uid")
				.setParameter("uid", userId)
				.getSingleResult();
		assertThat(crewIdInSession).as("upload_session.crew_id는 NULL").isNull();
	}

	@Test
	@DisplayName("CREW 타겟이 아닌 알림(VERIFICATION/CHALLENGE)은 삭제 후에도 남아 있어야 한다")
	@Transactional
	void deleteCrewWithAssociations_nonCrewNotificationsArePreserved() {
		// Given
		String userId = "integ-del-user-02";
		createUser(userId);
		Crew crew = createActiveCrew(userId);
		String crewId = crew.getId();
		String otherVerificationId = IdGenerator.generate("VRF");

		// CREW 타겟 알림
		notificationRepositoryPort.save(Notification.create(
				userId, NotificationType.CREW_STARTED,
				"크루 시작", "시작", NotificationTargetType.CREW, crewId));

		// VERIFICATION 타겟 알림 (다른 대상 — 삭제 후 보존되어야 함)
		notificationRepositoryPort.save(Notification.create(
				userId, NotificationType.VERIFICATION_APPROVED,
				"인증 승인", "승인", NotificationTargetType.VERIFICATION, otherVerificationId));

		entityManager.flush();

		// When
		crewRepositoryPort.deleteCrewWithAssociations(crewId);
		entityManager.flush();
		entityManager.clear();

		// Then — CREW 알림만 삭제, VERIFICATION 알림은 보존
		long crewNotifsAfter = countQuery(
				"SELECT COUNT(*) FROM notifications WHERE target_type = 'CREW' AND target_id = :id", crewId);
		assertThat(crewNotifsAfter).as("CREW 타겟 알림은 0건").isZero();

		long verificationNotifsAfter = countQuery(
				"SELECT COUNT(*) FROM notifications WHERE target_type = 'VERIFICATION' AND target_id = :id",
				otherVerificationId);
		assertThat(verificationNotifsAfter).as("VERIFICATION 타겟 알림은 보존(1건)").isEqualTo(1);
	}

	// ===== 헬퍼 =====

	/** 단일 :id 파라미터 COUNT 쿼리 */
	private long countQuery(String sql, String id) {
		Number count = (Number) entityManager.createNativeQuery(sql)
				.setParameter("id", id)
				.getSingleResult();
		return count.longValue();
	}

	/** 파라미터 없는 COUNT 쿼리 */
	private long countQueryNoParam(String sql) {
		Number count = (Number) entityManager.createNativeQuery(sql).getSingleResult();
		return count.longValue();
	}
}
