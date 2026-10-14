# DB Only · Redis Async 재현 가이드

## 1. 범위와 입력

이 문서는 새 실험의 수동 실행 절차다. 과거 결과·키·인프라 주소와 특정 run의 실행 카드는 포함하지 않는다. 새 머신/새 설정으로 원격 재현은 별도 검증 대상이다. 기존 실험은 고정 이미지/동일 설정으로 비교했고 실패·복구·재시도를 구분해서 보존했다.

준비할 값:

- `RUN_ID`: UTC `YYYYMMDD-HHMMSS-db|rd-50|200-hex4`, 매 run 새 값.
- 본 크루와 별도 워밍업 크루·초대코드, `ARM`, `RPS`, `OUT_DIR`.
- `BASE_URL`, `APP_SSH_TARGET`, 비공개 `APP_SSH_KEY`, 원격 절대경로 `EC2_RESULTS_ROOT`·`EC2_COLLECTORS_DIR`.
- 테스트 전용 `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE`, SSL·비공개 pgpass. 운영 계정/설정 재사용 금지.
- RD의 Redis 사설 연결 정보·`NAMESPACE`·같은 RUN_ID, `REDIS_CLI`.
- 비공개 env·GC 헤더·토큰 CSV. 파일0600, 상위 비공개폴더0700. 값은 콘솔·문서·Git에 기록하지 않는다.

[loadtest 앱 기동](runbooks/db-baseline-launch.md)에서 필수 환경값을 확인한다. 기존 코드의 Redis Worker·중복 DB 방지 로직은 develop에 있으며 여기서 수정하지 않는다.

## 2. 실행 순서

1. **대상 식별:** 테스트 서버·DB·Redis, 고정 이미지, 기존 프로세스·큐·수집기를 확인한다. 기존 작업이 있으면 중단하고 판단한다.
2. **최초 DB만:** [bootstrap SQL](sql/bootstrap/)을 검토 후 생성→V1~V26 스키마/사용자시드→권한 회수 순으로 실행한다. 기존 DB에는 재실행하지 않는다. SQL02는 BEGIN/SET LOCAL/COMMIT으로 시드를 처리한다.
3. **Mac 폴더·시계:** `run-folder.sh`로 새 OUT_DIR를 만들고 `clock-check.sh`의 offset을 기록한다. CPU/메모리·토큰 만료/개수·Git제외도 확인한다.
4. **크루 준비:** `21_b_crew_create.sql`로 본 정원1000/워밍업 정원100의 서로 다른 크루를 준비한다. 기존크루 재사용·삭제 금지. 기존 절차의 `23_b_vacuum.sql`·`24_b_baseline.sql`로 사전 상태를 기록한다.
5. **RD 초기화:** 새 키 부재 확인 후 `crew-join-redis.sh init`→`preflight`. 기존 키가 있으면 삭제/덮어쓰기 대신 중단한다.
6. **새 JVM:** 이전 원본 로그/컨테이너를 보존하고 같은 이미지로 새 컨테이너를 기동한다. DB는CONDITIONAL, RD는REDIS_ASYNC와 정확한 namespace/run-id. HealthUP와 Worker startup/recovery를 확인한다.
7. **워밍업 계측:** 워밍업 OUT_DIR에서 C8 시작, RD는 Redis EC2의 CPU도 시작한다. PID뿐 아니라 DB/Redis/TCP/CPU 정상 첫 표본과 Prometheus up을 확인한다.
8. **워밍업:** 아래 JSON을 `phase=warmup`으로 새로 준비하고 runner 실행. TOTAL1000은 runner가 설정한다. 목표1000건·오류/dropped0, 20199/CR002901. 실패 시 본 실행 보류.
9. **검증·GC:** 앱 자동 C8 stop 뒤 Redis CPU를 별도 종료한다. SQLI1~I7·Redis100/seq100/큐0/0·본크루1명 유지 확인. INFOafter·로그·Prometheus를 보존한다. 비공개 헤더로 `/internal/gc` 1회, HTTP200/GC증가/pending0 확인 후 안정화.
10. **본 READY:** 본 OUT_DIR에 C8·Redis CPU를 시작하고 TCPbefore·INFObefore·DB/락/CPU/Redis 첫 표본, PrometheusUP·앱 readiness를 확인한다. JSON의ready=true는 이 확인 결과를 뜻하며 강제로 바꾸는 게이트가 아니다.
11. **본 k6:** `phase=main`으로 runner 실행. TOTAL10000. 콘솔·JSON·summary·종료코드/시각 보존. 자동 C8 stop이 k6 종료 직후 이어진다. 수동채팅으로 종료를 기다렸다 끄지 않는다.
12. **RD SQL시점:** 요약 직후 별도 SSH 세션에서 `22_b_verify.sql`을 `sql-after.txt`로 1회 시도하고 실제 시작/종료 시각을 기록한다. 이미 drain 뒤면 그 사실을 기록하고 pre-drain이라고 주장하지 않는다.
13. **종료·검증:** pending/processing0와 DB1000 확인 후 최종SQLI1~I7, Redis/DB user-id 집합 차이0·중복0,Worker오류0를 확인한다. RedisCPU 별도 종료·INFOafter·앱 원본로그 보존.
14. **회수·봉인:** rsync로 새/비어 있는 대상에 회수하고 원격SHA256 대조, 잔존수집기0 확인, 동일 측정구간 Prometheus 추출. 실제결과/실패/예외를 기록한다. 원본은 덮어쓰지 않는다.

## 3. k6 실행 형식

| 목표 | PRE/MAX | 워밍업/본 총건수 |
|---|---|---|
| 50RPS |150/250|1000/10000|
| 200RPS |1000/1000|1000/10000|

같은 목표RPS의 DB/RD는 같은 VU 설정을 쓴다. constant-arrival-rate라 VU1000이 요청1000건 동시발사라는 뜻은 아니다. 응답 대기가 길면 실행자 점유가 늘어난다.

READY JSON의 필드는 `ready`, `phase`, `run_id`, `crew_id`, `target_rps`, `pre_vus`, `max_vus`, `clock_offset_ms`, `out_dir`, `tokens_file`이다. `out_dir`은 현재 저장소의 `load-test/results/raw/<UTC날짜>/<RUN_ID>` 절대경로여야 한다. 비밀값 대신 비공개 토큰 파일 경로를 넣는다.

```bash
python3 load-test/scripts/run-db.py /비공개설정/새-run-config.json
python3 load-test/scripts/run-rd.py /비공개설정/새-run-config.json
```

위 두 줄은 형식 예시다. 한 run에 DB/RD를 동시에 실행하지 않는다. `BASE_URL`, `APP_SSH_KEY`, `APP_SSH_TARGET`, `EC2_RESULTS_ROOT`, `EC2_COLLECTORS_DIR`은 미리 환경으로 주입한다. 해당 파일·주소를 기록하거나 공유하지 않는다.

## 4. 실패·복구

- k6 exit 0만으로 PASS가 아니다. 목표 iteration·dropped·transport·응답 분류·SQL·큐를 함께 본다.
- 기존 RUN_ID·결과·Redis 키·컨테이너 상태가 예상과 다르면 자동 초기화하거나 재실행하지 않는다.
- SSH 실패 시 로컬 결과·Prometheus·접근 가능한 Redis 증거부터 보존한다. C8 stop을 맹목적으로 재시도하지 않는다.
- 장애 복구는 별도 승인·계측으로 같은 RUN_ID를 처리한다. 단일 Worker, UNIQUE 제약, processing 회수 코드를 확인한다. 복구 성공은 원래 성능 측정의 성공과 구분한다.
- runner의 Ctrl-C 뒤에도 종료 계측이 이어질 수 있다. drain 중단은 C8 stopfile 절차를 사용하고 runner/stop을 중복 실행하지 않는다.
- `prom-export.sh`는 기존 prom JSON이 있으면 거절한다. 재추출은 동일 START/END의 새 하위 폴더에서 수행하고 stdout/stderr를 모두 보존한다.
- 원본 앱 로그에는 생성된 비밀번호가 있을 수 있다. 원본은 0600으로 비공개 보존하고 공유에는 마스킹 검토본을 사용한다.

## 5. 검증 범위

원본 실험에서 DB-50·RD-50·RD-200 재시도의 정합성과 회수를 확인했다. 실패 워밍업, DB-200 목표 부하 미달, RD-200 장애 후 복구도 별도로 보존했다. 과거 실측은 새 사본의 원격 실행 검증이 아니다. 이번 사본의 검증·미실행 항목은 [이식 기록](runbooks/asset-migration.md)에 명시한다. 다른 API를 테스트할 때는 요청·응답 분류·인증·데이터·정합성 SQL을 별도로 맞춘다.
