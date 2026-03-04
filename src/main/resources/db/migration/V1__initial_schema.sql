-- V1: 초기 스키마 생성
-- 주의: V2~V5 적용 전 상태 (users 테이블에 provider, terms_agreed_at 없음, email NOT NULL, id VARCHAR(255))

-- =============================================
-- User Context
-- =============================================

CREATE TABLE users (
    id               VARCHAR(255) NOT NULL PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    nickname         VARCHAR(255) NOT NULL,
    profile_image_url VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL
);

-- =============================================
-- Crew Context
-- =============================================

CREATE TABLE crews (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    creator_id        VARCHAR(36)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    goal              VARCHAR(255) NOT NULL,
    verification_type VARCHAR(255) NOT NULL,
    min_members       INT          NOT NULL DEFAULT 1,
    max_members       INT          NOT NULL,
    current_members   INT          NOT NULL,
    status            VARCHAR(255) NOT NULL,
    start_date        DATE         NOT NULL,
    end_date          DATE         NOT NULL,
    allow_late_join   BOOLEAN      NOT NULL,
    invite_code       VARCHAR(6)   NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    deadline_time     TIME         NOT NULL DEFAULT '23:59:59',
    CONSTRAINT uk_crews_invite_code UNIQUE (invite_code)
);

CREATE TABLE crew_members (
    id        VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id   VARCHAR(36) NOT NULL,
    crew_id   VARCHAR(36) NOT NULL,
    role      VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP   NOT NULL
);

CREATE TABLE challenges (
    id             VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id        VARCHAR(36)  NOT NULL,
    crew_id        VARCHAR(36)  NOT NULL,
    cycle_number   INT          NOT NULL,
    target_days    INT          NOT NULL,
    completed_days INT          NOT NULL,
    status         VARCHAR(255) NOT NULL,
    start_date     DATE         NOT NULL,
    deadline       TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL
);

-- =============================================
-- Verification Context
-- =============================================

CREATE TABLE upload_session (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    image_key    VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    status       VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL
);

CREATE TABLE verifications (
    id                VARCHAR(36)  NOT NULL PRIMARY KEY,
    challenge_id      VARCHAR(36)  NOT NULL,
    user_id           VARCHAR(36)  NOT NULL,
    crew_id           VARCHAR(36)  NOT NULL,
    upload_session_id BIGINT,
    image_url         VARCHAR(255),
    text_content      VARCHAR(500),
    status            VARCHAR(255) NOT NULL,
    report_count      INT          NOT NULL,
    target_date       DATE         NOT NULL,
    attempt_number    INT          NOT NULL,
    review_status     VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT uk_verifications_upload_session UNIQUE (upload_session_id),
    CONSTRAINT uk_verifications_user_crew_date UNIQUE (user_id, crew_id, target_date)
);

-- =============================================
-- Moderation Context
-- =============================================

CREATE TABLE reports (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    verification_id VARCHAR(36)  NOT NULL,
    reporter_id     VARCHAR(36)  NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    status          VARCHAR(255) NOT NULL,
    description     VARCHAR(255),
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_reports_verification_reporter UNIQUE (verification_id, reporter_id)
);

CREATE TABLE reviews (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    report_id     VARCHAR(36)  NOT NULL,
    reviewer_id   VARCHAR(36)  NOT NULL,
    reviewer_type VARCHAR(255) NOT NULL,
    decision      VARCHAR(255) NOT NULL,
    comment       VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL
);

-- =============================================
-- Support Context
-- =============================================

CREATE TABLE notifications (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL,
    type       VARCHAR(255) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    VARCHAR(255) NOT NULL,
    is_read    BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE reactions (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    verification_id VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    emoji           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

-- =============================================
-- 인덱스
-- =============================================

-- 크루 피드 조회 (인증 목록)
CREATE INDEX idx_verifications_crew_created
    ON verifications (crew_id, created_at DESC);

-- 신고 횟수 조회 (3건 -> REPORTED)
CREATE INDEX idx_verifications_report_count
    ON verifications (report_count)
    WHERE report_count >= 3;

-- 검토 대기 목록 조회
CREATE INDEX idx_verifications_review_status
    ON verifications (review_status, created_at DESC)
    WHERE review_status = 'PENDING';

-- 검토자별 검토 이력
CREATE INDEX idx_reviews_reviewer
    ON reviews (reviewer_id, created_at DESC);

-- 신고 상태별 조회
CREATE INDEX idx_reports_status
    ON reports (status, created_at DESC)
    WHERE status = 'PENDING';
