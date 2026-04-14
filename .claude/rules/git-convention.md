---
description: Git 브랜치 전략과 커밋 메시지 컨벤션 (AngularJS Convention, 한국어)
---

# Git Convention

## 브랜치 전략

- main: 운영 배포 (직접 push 금지, develop에서 PR로만 병합)
- develop: 통합 브랜치 (feat→develop PR, CI + E2E 통과 필수)
- feat/*: 기능 개발 브랜치 (develop에서 분기, develop으로 PR)
- fix/*: 버그 수정 브랜치 (develop에서 분기, develop으로 PR)

## 커밋 메시지 (AngularJS Convention)

**형식**
```
<type>: <한국어 메시지>
- 부연 설명 (선택, 최대 2줄)
```

**예시**
```
feat: 인증 기능 추가
- 사진 인증 시 presignedUrl 발급 로직 추가
```

```
fix: 크루 정원 초과 버그 수정
- SELECT FOR UPDATE 락 누락 수정
```

```
refactor: Verification 도메인 계층 분리
- UseCase와 Policy 클래스 분리
```

**커밋 타입**

| 타입 | 용도 |
|------|------|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 리팩토링 (기능 변경 없음) |
| test | 테스트 추가/수정 |
| docs | 문서 변경 |
| chore | 빌드, 설정 등 기타 |
