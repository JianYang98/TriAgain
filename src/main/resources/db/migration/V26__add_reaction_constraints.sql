-- 인증 리액션 쓰기 경로 개설에 필요한 제약
-- reactions 테이블 자체는 V1부터 존재하나 쓰기 경로가 없어 데이터 0건이다.
-- 선행: prod reactions 행수 0건 확인 (배포 전 실측 — 중복이 있으면 인덱스 생성이 실패해 부팅이 막힌다)
--
-- [매핑 판정] 이 유니크 위반은 upsert의 ON CONFLICT가 흡수하므로 예외로 표면화되지 않는다.
--            따라서 GlobalExceptionHandler.CONSTRAINT_ERRORS 에 엔트리를 추가하지 않는다
--            (2026-08 리액션 SDD 판정). 제약 실존은 통합테스트가 raw INSERT 2회로 검증한다.
--
-- [테스트 스키마 주의] 인수(Cucumber)·E2E 는 ddl-auto=create-drop + flyway off 라 이 파일이 실행되지 않는다.
--                    그 계층의 유니크 출처는 ReactionJpaEntity 의 @UniqueConstraint 이며 둘은 세트다.
--                    한쪽만 선언하면 다른 계층에서 ON CONFLICT 가 42P10 으로 실패한다.
--
-- 별도 조회 인덱스는 만들지 않는다 — 선두 컬럼(verification_id)이 피드 배치 조회(IN)를 커버한다.

CREATE UNIQUE INDEX uk_reactions_verification_user
    ON reactions (verification_id, user_id);
