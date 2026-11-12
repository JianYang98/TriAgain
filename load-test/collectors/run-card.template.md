# run card — {{RUN_ID}}

필드는 `sdd/crew-join-db-baseline/measurement-contract.md` §6과 같다. 시각은 UTC epoch ms. 비밀값(키·비밀번호·토큰)·IP는 적지 않는다.

| 필드 | 값 |
|---|---|
| run-id | {{RUN_ID}} |
| 팔 (DB Only \| Redis Async) | {{ARM}} |
| 목표 RPS | {{RPS}} |
| 앱 이미지 ID·태그 / 커밋 SHA (두 팔이 같아야 한다) | |
| 실행 인자 (비밀값 제외) | |
| accept-count | 미지정 |
| 관측한 리슨 큐 한도 (Send-Q, `tcp-delta.txt` 머리말) | |
| JVM (`java -version` · GC 종류 · 힙 최대 · 프로세스 시작 시각) | |
| 크루 id / 크루 정원 / 리더 user id | / / loadtest-leader |
| (Redis) namespace / runId | |
| DB 사전 행 수 (`crews` · `crew_members`) · 스키마 상태 | |
| 앱 시작 시각 | |
| 워밍업 (크루 id · 건수 · 결과) | |
| 강제 GC (응답 힙 전·후 · 소요 · HTTP 상태 · 워밍업 종료→GC→본 측정 시작 경과) | |
| k6 시작 · 종료 시각 (UTC) | |
| `T_last201` (근사 경계) | |
| `T_db_confirm` (또는 "미도달" + 사유, `poll-db.log`의 `T_DB_CONFIRM`·`END` 줄) | |
| drain 시간 | |
| 폴러 종료 사유 (`END reason=`) | |
| 맥 ↔ 테스트 EC2 시계 오프셋 (`clock-check.sh`) | |
| 맥의 장소 · RTT 샘플 | |
| k6 버전 | |
| 토큰 발급 시각 (24시간 만료 대비) | |
| CPU 크레딧 run 전 / 후 (Redis 팔은 drain 뒤) | |
| RDS Enhanced Monitoring 상태 (켬/끔/사유) | |
| TCP 수집 상태 (성공/사유) | |
| node_exporter · vmstat 상태 (I5 ◐, 성공/사유) | |
| `dropped_iterations` | |
| worker 중단 분류 (없음 / 환경 오류 / 처리 실패 / 판단 불가 — `test-plan.md` §7) | |
| 중단 · 이상 메모 | |
| 회수 체크 (EC2 → 맥) | [ ] |
| 종료 절차 체크 (step0 §4) | [ ] |
