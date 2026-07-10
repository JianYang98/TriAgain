-- =============================================
-- Habit Context (솔로 모드)
-- =============================================

CREATE TABLE habits (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    verification_type VARCHAR(255) NOT NULL,
    deadline_time     TIME         NOT NULL DEFAULT '23:59:59',
    status            VARCHAR(255) NOT NULL,   -- ACTIVE / PAUSED / ENDED (D10)
    created_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP                -- status=ENDED일 때 set, 지난기록 정렬 축 (D10, 기존 deleted_at 대체)
);

CREATE TABLE habit_cycles (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    habit_id       VARCHAR(36)  NOT NULL,
    user_id        VARCHAR(64)  NOT NULL,
    cycle_number   INT          NOT NULL,
    target_days    INT          NOT NULL,
    completed_days INT          NOT NULL,
    status         VARCHAR(255) NOT NULL,
    start_date     DATE         NOT NULL,
    deadline       TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL
);

CREATE TABLE habit_verifications (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    habit_cycle_id    VARCHAR(36)  NOT NULL,
    habit_id          VARCHAR(36)  NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    upload_session_id BIGINT,
    image_url         VARCHAR(255),
    text_content      VARCHAR(500),
    target_date       DATE         NOT NULL,
    attempt_number    INT          NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_habit_verifications_upload_session UNIQUE (upload_session_id)
);

-- 습관당 IN_PROGRESS 사이클 1개 (더블탭 방어) — V6 uk_challenges_user_crew_in_progress 대응
CREATE UNIQUE INDEX uk_habit_cycles_in_progress
    ON habit_cycles (habit_id)
    WHERE status = 'IN_PROGRESS';

-- 습관별 하루 1인증 — V1 uk_verifications_user_crew_date 대응 (습관은 단일 소유자라 habit_id 축으로 충분)
CREATE UNIQUE INDEX uk_habit_verifications_habit_date
    ON habit_verifications (habit_id, target_date);

-- 홈 목록 조회 (종료 안 된 습관만 — ACTIVE/PAUSED)
CREATE INDEX idx_habits_user
    ON habits (user_id)
    WHERE status <> 'ENDED';

-- 솔로 세션의 발급 컨텍스트 바인딩 (크루 세션의 crew_id 대응) — 크루 세션은 NULL
ALTER TABLE upload_session ADD COLUMN habit_id VARCHAR(36);
