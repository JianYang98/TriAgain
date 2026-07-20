-- 솔로 습관 인증 안내 문구 (지시서 05 #3) — crews.verification_content(V9) 대응.
-- nullable additive라 백필/NOT NULL 없음 (V9와 달리 기존 행은 안내 없음이 올바른 상태)
ALTER TABLE habits ADD COLUMN verification_content VARCHAR(100);
