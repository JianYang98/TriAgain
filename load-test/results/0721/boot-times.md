# 기동 증빙 장부 — CREW-JOIN TPS 6런 시리즈 (2026-07-21)

> 실행가이드 §2-2·§2-3 정본 기록. 라벨 확정은 이 파일의 부팅시각(epoch)으로만 한다.
> 공통 통제: 이미지 digest `sha256:ef45b502...` | accept-count **무지정=Tomcat 기본 100**(ss Send-Q=100 실증, somaxconn 4096)
> | GC 증빙 정본=프로메테우스 방법 3 (이미지에 jcmd 없음 — 07-21 실측, OCI exec not found)

| # | 용도 | 전략 | 부팅 epoch | KST 환산 | Args 원문 확인 | GC 증빙 | 비고 |
|---|------|------|-----------|----------|----------------|---------|------|
| 1 | 스모크 1 (CASE A) | CONDITIONAL | 1784602797 | 07-21 11:59:57 | lock-strategy=CONDITIONAL, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | Started in 25.663s / backlog Send-Q=100 실증 |
| 2 | 스모크 2 (CASE B) | PESSIMISTIC | 1784605467 | 07-21 12:44:27 | lock-strategy=PESSIMISTIC, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | 1차=구토큰 401 전량(사고, 재발급 후 재실행 통과: 197/104/dup0) |
| 3 | 본런 1 | PESSIMISTIC (A) | 1784610437 | 07-21 14:07:17 | lock-strategy=PESSIMISTIC, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |
| 4 | 본런 2 | OPTIMISTIC (A) | 1784610948 | 07-21 14:15:48 | lock-strategy=OPTIMISTIC, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |
| 5 | 본런 3 | CONDITIONAL (A) | 1784611336 | 07-21 14:22:16 | lock-strategy=CONDITIONAL, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |
| 6 | 본런 4 | CONDITIONAL (B) | 1784611691 | 07-21 14:28:11 | lock-strategy=CONDITIONAL, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |
| 7 | 본런 5 | OPTIMISTIC (B) | 1784612065 | 07-21 14:34:25 | lock-strategy=OPTIMISTIC, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |
| 8 | 본런 6 | PESSIMISTIC (B) | 1784612420 | 07-21 14:40:20 | lock-strategy=PESSIMISTIC, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | |

| 9 | 사다리(한계 탐색) r600/r800/r1000 | CONDITIONAL (A) | 1784614191 | 07-21 15:09:51 | lock-strategy=CONDITIONAL, accept-count 없음 ✓ | `gc="Copy"` (Serial) ✓ | 추가 시리즈 — 3스텝 공용 기동 (풀 10 기본) |
| 10 | 풀 스윕 pool=20 사다리 | CONDITIONAL (A) | 1784617411 | 07-21 16:03:31 | maximum-pool-size=20 (Args 원문 ✓) + `hikaricp_connections_max`=20.0 검증 | Serial (동일 이미지) | boot.sh 확장(추가 인자 통로) 후 첫 기동 |
| 11 | 풀 스윕 pool=30 사다리 | CONDITIONAL (A) | 1784617918 | 07-21 16:11:58 | maximum-pool-size=30 + `hikaricp_connections_max`=30.0 검증 | Serial (동일 이미지) | 세션 마지막 기동 — 종료 시 컨테이너 정리 대상 |

## 기동 #1 원문 (07-21)

```
process_start_time_seconds{application="triagain"} 1.784602797334E9
[-jar app.jar --spring.profiles.active=prod,loadtest --triagain.crew.lock-strategy=CONDITIONAL --spring.flyway.ignore-migration-patterns=*:missing,*:future]
jvm_gc_pause_seconds_count{action="end of minor GC",application="triagain",cause="Allocation Failure",gc="Copy"} 5
LISTEN 0 100 0.0.0.0:8080   (nsenter 컨테이너 netns ss -ltn / somaxconn=4096)
```
