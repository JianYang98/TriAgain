package com.triagain.habit.domain.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.triagain.common.exception.BusinessException;
import com.triagain.common.exception.ErrorCode;
import com.triagain.common.util.IdGenerator;
import com.triagain.habit.domain.vo.HabitStatus;
import com.triagain.habit.domain.vo.HabitVerificationType;

/** 습관 애그리거트 루트 — 사이클/인증은 별도 애그리거트(HabitCycle/HabitVerification)로 ID 참조만 유지 */
public class Habit {

	private static final int NAME_MAX_LENGTH = 50;
	private static final int VERIFICATION_CONTENT_MAX_LENGTH = 100;
	private static final LocalTime DEFAULT_DEADLINE_TIME = LocalTime.of(23, 59, 59);

	private final String id;
	private final String userId;
	private String name;
	private final HabitVerificationType verificationType;
	private final LocalTime deadlineTime;
	private HabitStatus status;
	private final LocalDateTime createdAt;
	private LocalDateTime endedAt;
	private final String verificationContent;

	private Habit(String id, String userId, String name, HabitVerificationType verificationType,
			LocalTime deadlineTime, HabitStatus status, LocalDateTime createdAt, LocalDateTime endedAt,
			String verificationContent) {
		this.id = id;
		this.userId = userId;
		this.name = name;
		this.verificationType = verificationType;
		this.deadlineTime = deadlineTime;
		this.status = status;
		this.createdAt = createdAt;
		this.endedAt = endedAt;
		this.verificationContent = verificationContent;
	}

	/** 습관 등록 — 사이클은 생성하지 않음(D3), status=ACTIVE로 시작. verificationContent는 선택(빈 값은 null 정규화) */
	public static Habit create(String userId, String name, HabitVerificationType verificationType,
			LocalTime deadlineTime, String verificationContent) {
		validateName(name);
		if (verificationType == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		LocalTime resolvedDeadlineTime = deadlineTime != null ? deadlineTime : DEFAULT_DEADLINE_TIME;
		return new Habit(
				IdGenerator.generate("HBIT"),
				userId,
				name,
				verificationType,
				resolvedDeadlineTime,
				HabitStatus.ACTIVE,
				LocalDateTime.now(),
				null,
				normalizeVerificationContent(verificationContent)
		);
	}

	/** 영속 데이터로 습관 복원 — DB 조회 결과를 도메인 객체로 변환 */
	public static Habit of(String id, String userId, String name, HabitVerificationType verificationType,
			LocalTime deadlineTime, HabitStatus status, LocalDateTime createdAt, LocalDateTime endedAt,
			String verificationContent) {
		return new Habit(id, userId, name, verificationType, deadlineTime, status, createdAt, endedAt,
				verificationContent);
	}

	/** 습관 이름 수정 — v1은 name만 변경 가능(verificationType/deadlineTime 변경 불가) */
	public void rename(String name) {
		validateName(name);
		this.name = name;
	}

	/** 습관 멈춤 — IN_PROGRESS 사이클 존재 여부는 다른 애그리거트라 서비스에서 검증(HB004) */
	public void pause() {
		this.status = HabitStatus.PAUSED;
	}

	/** 습관 재개 — PAUSED만 의미 있는 전이지만 ACTIVE에서 호출돼도 그대로 ACTIVE(no-op) */
	public void resume() {
		this.status = HabitStatus.ACTIVE;
	}

	/** 습관 종료 — status=ENDED·endedAt set(터미널, D10). IN_PROGRESS 사이클 fail() 처리는 서비스 책임 */
	public void end() {
		this.status = HabitStatus.ENDED;
		this.endedAt = LocalDateTime.now();
	}

	private static void validateName(String name) {
		if (name == null || name.isBlank() || name.length() > NAME_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	/** 인증 안내 문구 정규화 — 빈 값은 null(안내 없음)로, 100자 초과는 거부 (지시서 05 #3) */
	private static String normalizeVerificationContent(String verificationContent) {
		if (verificationContent == null || verificationContent.isBlank()) {
			return null;
		}
		if (verificationContent.length() > VERIFICATION_CONTENT_MAX_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return verificationContent;
	}

	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public HabitVerificationType getVerificationType() {
		return verificationType;
	}

	public LocalTime getDeadlineTime() {
		return deadlineTime;
	}

	public HabitStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public String getVerificationContent() {
		return verificationContent;
	}
}
