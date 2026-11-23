# TriAgain 부하 테스트 도구

## 크루 가입 RPS 테스트

자세한 설정과 실행 명령은 [RPS 테스트 절차](runbooks/rps-테스트-프로세스.md)를 참고하세요.

1. **토큰을 준비합니다.** 러너 EC2에서 토큰 파일을 읽을 수 있게 둡니다.
2. **크루를 준비합니다.** [prepare](scripts/prepare)에 목표 RPS, 크루 정원, 총 요청 수를 입력합니다.
   크루 준비가 끝나면 `RUN_ID`와 다음에 실행할 명령 블록 두 개(수집 명령과 부하 명령)가 출력됩니다.
3. **앱 EC2에서 수집을 시작합니다.** 수집 명령 블록을 붙여넣어 이번 `RUN_ID`로 `collect on`을 실행하고 정상 표본이 쌓이는지 확인합니다.
   [수집기 사용법](collectors/MANUAL-COLLECT.md)을 참고하세요.
4. **러너 EC2에서 가입 부하를 시작합니다.** 부하 명령 블록을 붙여넣습니다.
   [실행 스크립트](scripts/run-rps-join-test.sh)가 [가입 시나리오](k6/rps-join-test.js)를 실행하고 로그와 결과를 저장합니다.
5. **결과를 확인하고 수집을 종료합니다.** k6 요약과 필요한 증거를 확인한 뒤 앱 EC2에서 `collect off`를 실행합니다.

## 이전 실험과 결과

1. **DB Only·Redis Async** — [재현 가이드](DBB-REPRODUCTION-GUIDE.md)와 [결과 요약과 로컬 보관 규칙](results/RESULTS-INDEX.md)
2. **옛 스케줄러 실험** — 실행 스크립트와 시드 SQL은 AWS 실험 워크트리에 보관 중입니다.
3. **이전 가입 테스트 시나리오** — [B·C·C-D k6 스크립트](k6/archive/)

[스크립트 목록](DBB-SCRIPT-INVENTORY.md) · [앱 기동 절차](runbooks/db-baseline-launch.md) · [이식·검증 범위](runbooks/asset-migration.md)

실제 환경 변경·앱 기동·토큰 발급·k6 실행은 별도 작업이다. 저장소에 있는 준비/초기화/cleanup 파일을 일괄 실행하지 않는다. 기존 객체·결과 파일을 발견하면 덮어쓰기 대신 중단한다.
원본 데이터·인증값·개인 기록·실제 인프라 식별자는 로컬에 보관합니다.
