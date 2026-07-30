package com.triagain.crew.infra;

import com.triagain.crew.domain.model.CrewMember;
import com.triagain.crew.domain.vo.CrewRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

// [필수] 테스트(create-drop)의 유일한 인덱스 출처 — E2eTestBase: flyway.enabled=false + ddl-auto=create-drop
// → V22 마이그레이션 테스트에서 실행 안 됨. 유니크 인덱스는 이 어노테이션이 단일 출처.
@Entity
@Table(name = "crew_members",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_crew_members_crew_id_user_id",
				columnNames = {"crew_id", "user_id"}))
public class CrewMemberJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "crew_id", nullable = false, length = 36)
	private String crewId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CrewRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	protected CrewMemberJpaEntity() {
	}

	/** JPA 엔티티를 도메인 모델로 변환 */
	public CrewMember toDomain() {
		return CrewMember.of(id, userId, crewId, role, joinedAt);
	}

	/** 도메인 모델을 JPA 엔티티로 변환 — 저장 시 사용 */
	public static CrewMemberJpaEntity fromDomain(CrewMember member) {
		CrewMemberJpaEntity entity = new CrewMemberJpaEntity();
		entity.id = member.getId();
		entity.userId = member.getUserId();
		entity.crewId = member.getCrewId();
		entity.role = member.getRole();
		entity.joinedAt = member.getJoinedAt();
		return entity;
	}
}
