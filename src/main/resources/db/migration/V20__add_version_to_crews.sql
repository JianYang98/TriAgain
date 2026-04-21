-- 낙관적 락(Optimistic Locking) 지원을 위한 version 컬럼 추가
ALTER TABLE crews ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
