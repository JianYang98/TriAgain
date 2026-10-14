# DB Only · Redis Async 실험 도구

이 폴더는 기존 DB Baseline 실험 자산 중 재사용할 코드와 절차만 정리한 사본이다. 원본 데이터·인증값·개인 기록·실제 인프라 식별자는 포함하지 않는다.

1. [재현 가이드](DBB-REPRODUCTION-GUIDE.md)
2. [스크립트 목록](DBB-SCRIPT-INVENTORY.md)
3. [앱 기동 절차](runbooks/db-baseline-launch.md)
4. [결과 요약과 로컬 보관 규칙](results/RESULTS-INDEX.md)
5. [이식·검증 범위](runbooks/asset-migration.md)

실제 환경 변경·앱 기동·토큰 발급·k6 실행은 별도 작업이다. 저장소에 있는 준비/초기화/cleanup 파일을 일괄 실행하지 않는다. 기존 객체·결과 파일을 발견하면 덮어쓰기 대신 중단한다.
