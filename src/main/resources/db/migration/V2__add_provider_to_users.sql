-- users 테이블에 provider 컬럼 추가
-- 기존 행은 'LOCAL' 기본값 적용 (NOT NULL 제약조건 충족)
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
