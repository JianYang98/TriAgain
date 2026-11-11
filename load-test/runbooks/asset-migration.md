# 실험 자산 이식·검증 기록

## 반영 범위

최신 조회한 develop 커밋 `8375f72`에서 새 브랜치를 만들고 필요한 파일만 복사했다. 기존 실험 브랜치의 커밋 이력은 병합하거나 cherry-pick하지 않았다.

| 분류 | 반영 내용 |
|---|---|
| 실험 앱 지원 | loadtest 전용 GC와 테스트 로그인, Prometheus 의존성·노출 설정, 런타임 Dockerfile |
| 요청·데이터 | k6 응답 분류·토큰 입력, 크루 생성·정합성 SQL, 최초 DB bootstrap SQL |
| C8 계측 | TCP·DB·락·Redis·CPU 폴러, Prometheus/Grafana 설정·추출 |
| 직접 실행 | 기존 DB/RD runner와 로컬 mock 검사 |
| 문서 | 재현 순서·스크립트 목록·안전한 집계 결과·이식 범위 |

제외: raw 결과·토큰·환경 파일·인증 헤더·키·개인 실행 기록·실제 인프라 주소와 식별자·run별 하드코딩 wrapper. 기존 브랜치와 봉인 원본, 공용 실행 타임라인은 보존한다. 이전 브랜치에서 발견한 인증값 포함 이력은 가져오지 않는다.

## 사본에서 조정한 부분

- runner의 Mac/SSH/원격 경로를 환경 입력으로 바꾸고 저장소 기준 상대 위치를 사용했다. k6 실행·중단·자동 종료 계측 흐름은 재사용했다.
- mock은 가짜 k6/SSH와 임시 폴더를 사용한다. 실제 원격 명령이나 부하는 실행하지 않는다.
- loadtest 내부키·암호화키는 비공개 환경 입력으로 바꿨다. 지표 설정은 loadtest 프로필에 두었다. 공통 운영 설정은 변경하지 않았다.
- Dockerfile은 기존 런타임 사본이며 시간대 설정을 유지했다. 새 이미지를 빌드하지 않았다.
- 실제 실행 카드를 그대로 공개하지 않고, 재현 가이드에는 입력과 순서·중단 조건을 정리했다.

## 이번 사본의 검증

| 검사 | 결과 |
|---|---|
| C8 shell 16개 구문 검사 | PASS |
| Python 구문·설정 JSON | PASS |
| DB/RD runner 로컬 mock | 36개 PASS: 정상·실패·중단·종료 실패 등 |
| Java Checkstyle main/test | PASS, 오류 0 |
| Java compileJava/compileTestJava/test | Gradle 전체 작업 PASS; 전체 테스트 개수는 별도 집계하지 않음 |
| GC 컨트롤러 대상 테스트 | 1개 PASS |

새 사본으로 원격 실행·배포·이미지 빌드·E2E·재측정은 하지 않았다. 과거 실측은 과거 동결 이미지의 결과이며 이 사본의 원격 검증 결과로 대체하지 않는다. 민감정보 검사는 커밋 후보 파일을 대상으로 알려진 비밀값·토큰 대조와 인프라/키 형태 검사를 수행한다. 모든 미지의 비밀 부재를 보증하는 검사는 아니다.

## DB only 조기 거절 도구 이식 (2026-11-11)

AWS 조기 거절 실험 워크트리의 미추적 도구 9개를 SAFE로 옮겼다. 원본은 `triagain-backups/loadtest-scripts/2026-11-11/aws-early-reject/`에 sha256 목록과 함께 백업돼 있고, 옮긴 파일은 비공개 값 3줄(아래 ✎)만 원본과 다르다.

### 배치명 대응표

원격에는 실험 당시 이름으로 복사돼 있었다. 560c 실행 기준이다. 대응은 호출 인자(`run`·`mode`·`READY.json` 등)와 `manual-runner.py`·`manual-load.sh`의 경로 문자열로 맞췄다. 원격 파일의 해시와는 대조하지 못했다.

| SAFE 경로 | 원격 배치명 | 위치 |
|---|---|---|
| `scripts/early-reject/manual-load.sh` ✎ | — (Mac에서 직접 실행) | Mac |
| `scripts/early-reject/abort-load.sh` ✎ | — (Mac에서 직접 실행) | Mac |
| `scripts/early-reject/manual-runner.py` | `/home/ec2-user/triagain-k6/load-test/scripts/manual-early-reject-150-560c.py` | 러너 |
| `scripts/early-reject/manual-app-monitor.py` ✎ | `/home/ec2-user/early-reject-manual-150-560c.py` | 앱 |
| `scripts/run-early-reject.py` | `/home/ec2-user/triagain-k6/load-test/scripts/run-early-reject-3ebff4.py` | 러너 |
| `scripts/finish-early-reject.py` | 확인하지 못함 | — |
| `sql/21_early_reject_crew_create.sql` | — | — |
| `collectors/raw-scrape.py` | `/home/ec2-user/raw-scrape-3ebff4.py` | 러너 |
| `collectors/ss-300ms.py` | `/home/ec2-user/ss-300ms-3ebff4.py` | 앱(컨테이너 netns 안) |

✎ = 비공개 값 줄을 env 이름으로 바꿨다. `manual-load.sh`·`abort-load.sh`는 키 경로와 접속 대상을 `RUNNER_SSH_KEY`·`RUNNER_SSH_TARGET`으로, `manual-app-monitor.py`는 RDS 호스트 상수를 `manual-phase.json`의 `db.host`와 비교하게 바꿨다(비교 가드는 그대로, 값이 비어 있어도 중단). 필요한 env 이름은 `scripts/early-reject/aws.env.example`에 있다(값 없음).

### 원격 버전 미확정

560c 원격에서 실제로 돌았던 파일이 위 9개와 같은지는 확인하지 못했다. 560c의 sha256 목록 3개(`app`·`runner`·`local`)에 도구 파일명이 없고, 세션 문서(`manual-runbook.md`·`preparation.json`)에도 도구 파일 해시가 없다. 같은 이름의 원격 파일 해시를 대조하기 전까지는 "원본 백업과 같다"까지만 말할 수 있다. 원격 접속은 이번 이식 범위가 아니다.

### 쓰기 전에 알아 둘 점

- **560c 상수가 남아 있다.** `manual-runner.py`·`manual-app-monitor.py`는 RUN ID(`…-db-50-0613`·`…-db-150-560c`)와 토큰 수 9,000이 박혀 있어 다른 실험에 쓰려면 상수를 바꿔야 한다. 위쪽 "제외: run별 하드코딩 wrapper" 원칙의 예외다.
- **`manual-phase.json`에 `"db": {"host": ...}`를 넣어야 한다.** `manual-app-monitor.py`가 앱 박스의 이 파일(이미 `phase`·`app`·`crew_id`를 읽는다)에서 `db.host`를 읽어 컨테이너 `DB_URL`의 호스트와 대조한다. env는 쓰지 않는다. 560c 6개 RUN의 `manual-phase.json`에는 `db` 키가 없다(raw 백업 확인) — 그 실험에서는 호스트가 스크립트에 박혀 있었다. 키가 없으면 `KeyError`로 중단한다. 이 저장소에는 `manual-phase.json`을 만드는 코드가 없다. `db_name`·`db_user`는 원본대로 코드에 남아 있다.
- **`pre` 단계는 `/internal/gc`를 부른다.** `GcTriggerController`는 `@Profile("loadtest")` 테스트 이미지(lab·AWS 브랜치 빌드) 전용 엔드포인트다. 이 도구는 그 이미지에서만 `pre`가 통과한다.
- **`manual-early-reject.py`는 옮기지 않았다.** `manual-runner.py`와 RUN ID(`e294`·`ffd9`)·토큰 수 6,000·원격명 `early-reject-manual-3ebff4.py` 3곳만 다르다. 백업(`aws-early-reject/load-test/scripts/`)에만 있다.
- **`run-early-reject.py`·`finish-early-reject.py`는 `run-db-c-d.py`·`finish-db-c-d.py`와 합치지 않고 따로 뒀다.** 뒤쪽 둘은 이미 추적 중인 기존 도구라 바꾸지 않는다. `run-early-reject.py`의 Usage 문구는 원본대로 `run-db-c-d.py`라고 적혀 있다.

## 후속 개선 제안

지금은 리팩터링하지 않았다. 다음 검토에서는 run별 wrapper의 하드코딩 제거, Redis CPU 종료 누락 방지, drain 전 SQL 취득 시점, Prometheus 재추출 폴더 규칙을 우선 다룬다. 원본과 실패 증거를 유지하면서 각각 검토한다.

[재현 가이드](../DBB-REPRODUCTION-GUIDE.md) · [스크립트 목록](../DBB-SCRIPT-INVENTORY.md)
