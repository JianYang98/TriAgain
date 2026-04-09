-- SEC-C1 (2026-04-09 PR review): Apple refresh_token AES-256-GCM 암호화 적용.
-- 암호문 + IV(12B) + tag(16B) + Base64 + "v1:" prefix → 컬럼 길이 확장 필요.
-- 운영 데이터 없음 확인 후 단순 ALTER (backfill 불필요).

ALTER TABLE users ALTER COLUMN apple_refresh_token TYPE VARCHAR(1024);