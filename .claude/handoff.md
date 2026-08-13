# Handoff: MyBatis 잔재 정리 (완료)

> 브랜치: `develop` (작업 브랜치 `chore/remove-mybatis-leftover` → PR #149 머지 완료, 정리됨)
> 세션: 2026-08-11

---

## 이번 세션 완료 작업

### MyBatis 폐기 — PR #149 (머지: `b9e7bce`)

지시서 `triagain/revisions/17-mybatis-잔재-정리.md` 실행. Tier 2 / Light Track.
초기 셋업 커밋 `668eff1`(2026-02-22)이 넣은 MyBatis가 **매퍼 XML 0건 · `@Mapper` 0건 · `SqlSession` 0건**인 채
6개월 방치돼 있었다. 복잡 조회는 네이티브 쿼리(11파일 33곳)가 실질 규칙 — 폐기 확정.

**코드·설정 (5건)**

| 위치 | 내용 |
|---|---|
| `build.gradle` | `mybatis-spring-boot-starter:3.0.5` 제거 |
| `application.yml` | `mybatis:` 블록 + `default_batch_fetch_size: 100` 제거 |
| `src/main/resources/mybatis/` | `mybatis-config.xml` + 디렉토리 제거 |
| `common/config/MyBatisConfig.java` | `@MapperScan` 설정 클래스 제거 |

**문서 (8곳)** — 하네스 트랙 5파일(`CLAUDE.md`·`anti-patterns`·`coding-convention`·`config-deploy`·`db-migration`)
+ `architecture.md`·`handoff.md`(3곳)·`future-considerations.md`. debugging-log에 판단 기록 prepend.

---

## 이번 세션의 판단

1. **`default_batch_fetch_size`도 같이 걷었다.** 연관관계 애너테이션이 src 전체 0건이고
   `@Entity` 14개가 FK를 원시 ID 필드로 들고 있어 지연 로딩 자체가 없다 — 지금 무효인 설정을
   남기면 "N+1 대비가 되어 있다"는 반대 신호를 준다.
   ⚠️ **나중에 `@OneToMany` 등을 처음 도입하는 사람은 이 설정이 없다는 걸 알고 시작해야 한다.**

2. **`future-considerations`의 MyBatis 이관 항목은 미루기가 아니라 종결(`✅ RESOLVED`)로 처리.**
   이관의 목적지가 사라졌으므로 현재 상태가 곧 정답이다.

3. **지시서가 틀렸던 것 3건을 실측으로 잡아 지시서에 반영**(orchestrator가 갱신):
   - 문서 2곳 누락(`coding-convention.md`·`CODEX.md`) — 경로 나열식 grep이 못 봤다
   - "`./gradlew test`가 E2E까지 판정한다"는 **사실이 아니다**(`excludeTags 'e2e'`)
   - yml 3줄 삭제가 flyway 좌표를 `21-23`→`18-20`으로 밀어 규칙 파일 2곳 인용을 낡게 만든다

4. **검증에 음성 대조를 넣었다(§D-7).** 정적 grep 0건은 스타터가 클래스패스에 남아 있어도 참이다.
   제거 전 테스트 XML의 `No MyBatis mapper was found` WARN **16개**(test·e2eTest 계열)를
   양성 대조로 잡아두고, 제거 후 같은 명령이 0건인 것으로 런타임 제거를 판정했다.

---

## 상태

- **git**: `develop` 동기, 미푸시 커밋 0건, 워크트리 1개
- **브랜치 정리**: 머지 끝난 로컬 5개·원격 6개 삭제 (`chore/logging-rules-length`, `chore/config-deploy-rule`,
  `docs/deploy-healthcheck-note`, `docs/session-log-20260811`, `docs/import-order-and-tabify-log`,
  `chore/remove-mybatis-leftover`). 남은 로컬: `develop`·`main`·랩 3개(`feat/load-test`,
  `feat/load-test-2`, `backup/feat-load-test-pre-develop-merge` — develop 머지 금지)
- **검증**: checkstyle 0 / compile 통과 / `test` 126클래스 0실패 / `e2eTest` 7클래스 16테스트 0실패 / CI·CodeRabbit pass

---

## 다음 단계

1. ~~**`CODEX.md` 처리 방침 결정**~~ — ✅ **해소** (PR #150 `chore/remove-codex-md`, `412c93b`).
   파일 삭제 + `.gitignore` 제외 항목 제거로 마무리됐다. 아래 "주의사항" 절 참조.
2. 지시서 §F의 orchestrator 후속 질문 2건 (하네스 트랙 별건, 사용자 승인 필요):
   - `config-deploy.md:10-11` "이 영역은 Tier 3" 문구를 tier-policy의 보안/비보안 세분화와 정합하게 좁힐지
     (이번에 tier-policy는 Tier 2, config-deploy.md는 Tier 3으로 **충돌**했고 Tier 2로 진행했다)
   - 규칙 파일의 라인 좌표 인용을 키 이름 인용으로 바꿔 다시 낡지 않게 할지 (이번 B8이 그 실사례)
3. FE 저장소 MyBatis 8곳 — **고아 시점 기록물로 확정, 수정하지 않기로 함**(지시서 §F).
   FE 문서 정리(고아 사본 삭제 vs BE 정본 링크 대체)는 별건.

---

## 주의사항

### ~~`CODEX.md`가 git 밖에 있다~~ → ✅ 해소 (PR #150, `412c93b`)

**선택된 안: 삭제.** 파일을 지우고 `.gitignore:30`의 제외 항목도 함께 걷었다(실측: `CODEX.md` 부재 ·
`origin/develop` 트리에 없음).

🔴 **그런데 #150은 구멍을 하나 냈고, 이 문단이 처음엔 그걸 덮었다** (2026-08-13, PR #153 Codex 리뷰가 잡음).
초안에 *"Codex는 이제 `CLAUDE.md`를 읽는다"* 라고 썼으나 **검증 없이 쓴 거짓이다.**
**Codex가 탐색하는 이름은 `AGENTS.override.md` → `AGENTS.md` 순서고, `CLAUDE.md`는 그 목록에 없다.**
#150 이후 이 저장소엔 둘 다 없었으므로 **Codex는 프로젝트 지시서 없이 PR을 리뷰해 왔다.**

→ 해결: 루트에 `AGENTS.md` 를 **`CLAUDE.md` 심볼릭 링크**(git mode `120000`)로 둔다.
내용을 복사하지 않고 가리키므로 두 벌이 갈라질 수 없다(Codex는 symlink를 따라간다 — 실측 `git ls-files -s AGENTS.md`).

아래는 판단 근거로 남긴 원문이다.


- `.gitignore:30`에 걸려 있어 **커밋·PR·리뷰를 한 번도 안 거친다.**
- 그래서 6개월간 아무도 안 봤고, 포트-어댑터 표에 **실존한 적 없는 클래스
  `VerificationMyBatisAdapter`**가 남아 있었다. 하필 이 파일을 먹는 게 PR 리뷰를 시키는 **Codex**다.
- PR #150 이전에는 6곳의 수정본(2026-08-11)이 이 머신에만 있었고, 다른 머신·새 클론에는 낡은 내용이 남아 있었다.
- 당시 선택지: 추적 대상으로 전환 / 삭제 / 현행 유지(계속 낡음). → **삭제**를 골랐고,
  Codex를 어디로 보낼지는 그때 정하지 않은 채 남았다 — 그게 위의 구멍이다.

### 옛 인계서 항목 정리

이전 인계서(2026-08-04)의 "크루 최소 기간 미검증 버그"는 **해소됐다** —
`Crew.java:296-298`이 `endDate.isBefore(startDate.plusDays(6))` → `CREW_DURATION_TOO_SHORT`를 던진다(실측).
