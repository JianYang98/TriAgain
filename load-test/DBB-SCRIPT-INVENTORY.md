# 재사용 스크립트 목록

## 공통 도구

| 파일 | 실행 위치 | 입력 | 출력·주의 |
|---|---|---|---|
| [run-db.py](scripts/run-db.py) / [run-rd.py](scripts/run-rd.py) | Mac/부하 생성기 | READY JSON·BASE_URL·APP_SSH_KEY·APP_SSH_TARGET·EC2_RESULTS_ROOT·EC2_COLLECTORS_DIR | k6 콘솔/JSON/summary/종료시각, 자동 C8 stop. 기존 결과 있으면 거절 |
| [db-baseline-b.js](k6/db-baseline-b.js) | k6 실행 위치 | BASE_URL CREW_ID TARGET_RPS TOTAL PRE_VUS MAX_VUS WARMUP TOKENS_FILE RUN_ID OUT_DIR | 응답 분류·summary. 사용자 풀 되감기 없음 |
| [run-folder.sh](collectors/run-folder.sh) | Mac/앱 EC2 | RUN_ID ARM RPS OUT_DIR | 새 폴더·run-card. 기존 비어 있지 않은 경로 거절 |
| [clock-check.sh](collectors/clock-check.sh) | Mac | APP_SSH_TARGET SSH | offset/RTT·chronyc. 매 run 새 표본 |
| [box-app-start.sh](collectors/box-app-start.sh) | 앱 EC2 | RUN_ID ARM CREW_ID EXPECTED_TOTAL APP_CONTAINER OUT_DIR PG*, RD는 REDIS_CLI/NAMESPACE | 앱 CPU·DB·락·Redis·TCP 시작 |
| [box-app-stop.sh](collectors/box-app-stop.sh) | 앱 EC2, runner에서 자동 호출 | RUN_ID OUT_DIR K6_END_MS CLOCK_OFFSET_MS | TCP after·DB/Redis drain 대기·앱 CPU 종료. Redis EC2 CPU는 별도 종료 |
| [poll-db.sh](collectors/poll-db.sh) / [poll-lock.sh](collectors/poll-lock.sh) | 앱 EC2 | 테스트 DB libpq 환경·비공개 pgpass·run/crew | 1초 DB 인원·락 원본 |
| [poll-redis.sh](collectors/poll-redis.sh) | 앱 EC2 | REDIS_CLI NAMESPACE RUN_ID CREW_ID EXPECTED_TOTAL MAX_WAIT_SEC | members/seq/pending/processing·DB 최신 표본 확인 |
| [tcp-collect.sh](collectors/tcp-collect.sh) / [tcp-delta.sh](collectors/tcp-delta.sh) | 앱 EC2 | APP_CONTAINER PORT OUT_DIR | namespace/host nstat·ss·Accept Queue·증가량 |
| [mpstat-run.sh](collectors/mpstat-run.sh) / [vmstat-run.sh](collectors/vmstat-run.sh) | 앱·Redis EC2 | RUN_ID OUT_DIR, Redis OUT_NAME | CPU/메모리·swap 원본·PID·STOP |
| [prometheus-up.sh](collectors/prometheus-up.sh) / [prometheus-down.sh](collectors/prometheus-down.sh) | Mac | APP_TARGET 또는APP_PUBLIC_IP·PROM_DATA_DIR, 선택Grafana 비공개 비밀번호 | loopback Prometheus/Grafana. 기존 컨테이너 충돌 시 중단. down 동작은 별도 확인 후 실행 |
| [prom-export.sh](collectors/prom-export.sh) | Mac | START_MS END_MS OUT_DIR | 같은 구간 prom JSON. exit0성공/3node경고/그외실패. 기존prom파일이 있으면 새 하위폴더 사용 |
| [rds-os-export.sh](collectors/rds-os-export.sh) | AWS 조회 권한이 있는 환경 | 스크립트 usage에 정의한 RDS 식별자·시간 | 선택적 Enhanced Monitoring 회수. 이번 정리에서 AWS 호출하지 않음 |
| [crew-join-redis.sh](../scripts/crew-join-redis.sh) | 앱 EC2 | init/preflight·NAMESPACE/RUN_ID/crew·PSQL/REDIS_CLI | 기존제품 스크립트 사용. 초기화 전 새 run 키 부재 확인 |

`common.sh`, `queries.promql`, Grafana/Prometheus 설정은 C8와 함께 배치한다. 파일 하나만 떼어 복사하지 않는다. 실험 대상 값은 환경변수로 공급하며 실제 IP·키 경로·인증값은 Git에 넣지 않는다.

## SQL

- 최초 bootstrap: [01-create.psql](sql/bootstrap/01-create.psql) → [02-schema-and-seed.psql](sql/bootstrap/02-schema-and-seed.psql) → [03-finalize-permissions.psql](sql/bootstrap/03-finalize-permissions.psql). 최초 생성 대상 부재·테스트 DB 식별을 요구한다. DB/계정명은 스크립트에 정의된 실험 전용 이름이며 이미 존재하는 DB에는 다시 실행하지 않는다.
- 매 run: [20_b_leader_user.sql](sql/20_b_leader_user.sql), [21_b_crew_create.sql](sql/21_b_crew_create.sql), [23_b_vacuum.sql](sql/23_b_vacuum.sql), [24_b_baseline.sql](sql/24_b_baseline.sql), [22_b_verify.sql](sql/22_b_verify.sql). 실제 순서는 가이드 참조.
- [29_b_cleanup_crew.sql](sql/29_b_cleanup_crew.sql)은 명시적 정리용이며 기본 재현 절차에 포함하지 않는다. 봉인 결과·DB를 삭제하려고 자동 호출하지 않는다.

## 검증 상태

C5/C7/C8와 기존 runner 흐름은 원본 실험에서 실제 사용했다. 새 사본의 실행기는 인프라/경로를 외부 입력으로 바꿨으므로 원본 실측을 그대로 새 사본의 원격 실행 검증으로 주장하지 않는다. 새 브랜치의 구문·mock/Java 검사 결과는 [이식 기록](runbooks/asset-migration.md)에서 구분한다. 특정 run의 환경 준비·GC·회수 wrapper는 이식하지 않았으며 아래 가이드의 수동 단계를 따른다.
