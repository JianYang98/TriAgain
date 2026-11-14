# main 자동 배포와 develop 수동 운영 배포

> 2026-11-14. 사용자 승인: 기존 EC2 배포 구조 유지, develop 자동 배포 제거,
> 브랜치별 이미지 태그 분리, 공통 concurrency 적용. 머지·실제 배포는 별도 사용자 결정이다.

[TIER 판정] Tier: 3 | 근거: "배포 워크플로의 배포 경로·트리거 변경"

## 목표와 실행 계약

| 이벤트 | 대상 | 백엔드 | Lambda |
|---|---|---|---|
| PR | main·develop | CI → E2E만 실행 | 생략 |
| push | main | latest 자동 배포 | lambda/** 변경 시 배포 |
| push | develop·기타 | 워크플로 시작 안 함 | 실행 안 함 |
| workflow_dispatch | develop | develop 수동 배포 | 생략 |
| workflow_dispatch | main·기타 브랜치·태그 | 배포 생략 | 생략 |

- 브랜치와 이벤트를 배포 job의 if에서 함께 검사한다.
- 이미지 태그는 별도 shell 단계에서 main=latest, develop=develop으로 명시적으로 선택한다.
  그 외 ref는 실패한다. GitHub 작업 머신에서 선택한 값을 SSH envs로 EC2에 전달한다.
- SHA 태그도 계속 업로드한다. 실제 pull/run은 브랜치별 태그를 사용하므로
  SHA 기반 배포·자동 rollback 도입은 이번 변경 범위가 아니다.
- 같은 EC2·prod 프로필·triagain 컨테이너·8080 포트와 기존 환경변수·로그·볼륨을 유지한다.
  develop 수동 배포도 운영 버전을 교체한다. 별도 개발 환경을 만드는 변경이 아니다.
- 백엔드 job 전체를 공통 concurrency 그룹 triagain-production-backend로 직렬화한다.
  cancel-in-progress=false로 실행 중인 배포를 중단하지 않는다. 기본 대기 슬롯은 하나이며,
  새 요청이 기존 대기 요청을 대체할 수 있다. 나중에 실행된 배포가 최종 운영 버전이 된다.
- PR CI·E2E와 main의 Lambda 변경 감지는 유지한다. 수동 실행에 Lambda 배포를 추가하지 않는다.
- Firebase 게이트·health 판정·컨테이너 교체 방식의 기존 한계는 별도 과제로 유지한다.

## 적용 순서와 검증

1. origin/develop에서 분기한 작업 브랜치에서 변경하고 develop 대상 PR로 검토한다.
2. 새 워크플로가 develop에 병합되면 해당 push는 자동 배포를 시작하지 않는다.
3. 기본 브랜치 main에도 workflow_dispatch가 반영되어야 Actions의 수동 실행 버튼을 사용할 수 있다.
   develop → main 릴리스 PR에는 기존 미릴리스 변경도 포함되므로 배포 전에 전체 범위를 검토한다.
   main 병합 자체는 main 자동 배포를 시작한다.
4. 기존 버전 워크플로로 시작된 실행과 EC2 직접 배포는 새 concurrency의 보호를 받지 않는다.

검증은 YAML/Actions 구문 검사와 이벤트·ref별 실행 행렬, 실제 태그 선택 shell 실행,
SSH 전달·pull/run 일치 검사로 수행한다. 원래 워크플로는 develop push 배포 금지와
develop 수동 배포 허용 검증에 실패해야 한다. 애플리케이션 컴파일·테스트도 확인하되,
Docker·테스트 DB 등 환경 제약이 있으면 미검증 범위를 결과에 명시한다.

## 로컬 검증 결과

- actionlint 1.7.12: 통과.
- 이벤트·ref·Lambda 변경 유무 22개 조합: 통과. 변경 전 워크플로는 develop push와 수동 실행의
  각 2개 조합, 총 4개에서 새 계약을 만족하지 못함을 확인했다.
- 실제 태그 선택 Bash: main=latest, develop=develop, 다른 브랜치·태그 거부 확인.
- SHA 태그 유지, SSH envs 전달, pull/run 이미지 일치, 기존 EC2 옵션과 CI·E2E·Lambda job 보존 확인.
- `./gradlew compileJava compileTestJava -x test`: 통과.
- `./gradlew test`: 761개 중 132 실패·26 생략. Docker daemon 부재로 Testcontainers 초기화와
  연쇄 ApplicationContext 로딩 실패. 전체 테스트 통과는 확인하지 못했다.
- `./gradlew e2eTest`: 30개 중 24 실패. Docker daemon 부재로 DB 기반 E2E 검증을 완료하지 못했다.
- 실제 Docker 이미지 빌드·SSH·운영 배포는 실행하지 않았다.
