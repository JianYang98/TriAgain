package com.triagain.support.infra;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// 스키마 선언 전용 — 읽기·쓰기는 전부 네이티브 SQL(upsert·요약 projection)이라 게터도 도메인 변환도 없다.
// [필수] 테스트(create-drop)의 유일한 인덱스 출처 — CucumberSpringContext·E2eTestBase: flyway off + ddl-auto=create-drop
// → V26 마이그레이션이 인수·E2E에서 실행 안 됨. 이 어노테이션이 빠지면 upsert의 ON CONFLICT가 42P10으로 실패한다.
@Entity
@Table(name = "reactions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_reactions_verification_user",
				columnNames = {"verification_id", "user_id"}))
public class ReactionJpaEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "verification_id", nullable = false, length = 36)
	private String verificationId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(nullable = false)
	private String emoji;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReactionJpaEntity() {
	}
}
