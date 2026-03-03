# 시퀀스 다이어그램 - 크루 가입

## 1. 전체 흐름 (간략)

```mermaid
sequenceDiagram
    participant Client
    participant Facade
    participant Idem as Idempotency
    participant Lock
    participant Service
    participant DB

    Client->>Facade: 크루 참여

    Facade->>Idem: 중복 체크
    alt 중복
        Idem-->>Client: 이미 참여됨
    else 신규
        Facade->>Lock: tryLock()

        alt 성공
            Lock-->>Facade: OK
            Facade->>Service: 비즈니스 로직
            Service->>DB: SELECT FOR UPDATE
            Service->>DB: INSERT + UPDATE
            Service-->>Facade: 완료
            Facade->>Idem: 결과 저장
            Facade->>Lock: unlock()
            Facade-->>Client: 성공

        else 실패
            Lock-->>Facade: 대기
            Note over Facade: Backoff + Retry
        end
    end
```

## 2. 크루 가입 상세

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client
    participant Controller as CrewController
    participant Facade as CrewJoinFacade<br/>(Lock & Idempotency 관리)
    participant IdemStore as IdempotencyStore<br/>(Redis)
    participant Lock as RedisLock<br/>(TTL=1000ms)
    participant Service as CrewJoinService
    participant CrewRepo as CrewRepository
    participant MemberRepo as CrewMemberRepository
    participant DB as PostgreSQL

    Client->>Controller: POST /crews/{crewId}/join<br/>(Idempotency-Key, userId)

    Note over Controller: 1) Request 검증
    Controller->>Facade: joinCrew(userId, crewId, idemKey)

    Note over Facade,IdemStore: 2) Idempotency 선검증 (Fast Fail)
    Facade->>IdemStore: get(idemKey)

    alt 이미 COMPLETED
        IdemStore-->>Facade: 기존 응답 존재 ✅
        Facade-->>Controller: Cached Response
        Controller-->>Client: 200 OK (이미 참여됨)

    else 이미 IN_PROGRESS
        IdemStore-->>Facade: IN_PROGRESS 상태
        Facade-->>Controller: 409 Conflict
        Controller-->>Client: 처리 중입니다

    else 최초 요청 (신규)
        IdemStore-->>Facade: null

        Note over Facade,IdemStore: 3) Idempotency Key 저장
        Facade->>IdemStore: set(idemKey, IN_PROGRESS, TTL=1h)

        Note over Facade,Lock: 4) Distributed Lock Acquire
        Facade->>Lock: tryLock("crew:" + crewId, TTL=1000ms)

        alt Lock 획득 성공
            Lock-->>Facade: true ✅

            Note over Facade: Lock 획득 완료 → 비즈니스 로직 수행
            Facade->>Service: joinCrewInternal(userId, crewId)

            Note over Service,DB: 5) DB Pessimistic Lock (정원 체크)
            Service->>CrewRepo: findByIdWithLock(crewId)
            CrewRepo->>DB: SELECT * FROM crew<br/>WHERE id = :crewId<br/>FOR UPDATE
            DB-->>CrewRepo: Crew (행 락 획득)
            CrewRepo-->>Service: Crew (currentMembers=9, maxMembers=10)

            Note over Service: 6) 정원 체크
            Service->>Service: if (currentMembers >= maxMembers)<br/>throw FullCrewException

            alt 정원 초과
                Service-->>Facade: FullCrewException
                Facade->>Lock: unlock("crew:" + crewId)
                Facade->>IdemStore: delete(idemKey)
                Facade-->>Controller: 400 Bad Request
                Controller-->>Client: 정원 초과

            else 참여 가능
                Note over Service,DB: 7) CrewMember 생성 & 인원 증가
                Service->>Service: member = CrewMember.create(userId, crewId)
                Service->>Service: crew.incrementMembers() → 10

                Service->>MemberRepo: save(member)
                MemberRepo->>DB: INSERT INTO crew_member<br/>(UNIQUE: userId + crewId)
                DB-->>MemberRepo: saved

                Service->>CrewRepo: save(crew)
                CrewRepo->>DB: UPDATE crew<br/>SET current_members = 10
                DB-->>CrewRepo: updated

                Service-->>Facade: CrewJoinResponse

                Note over Facade,IdemStore: 8) 성공 결과 저장
                Facade->>IdemStore: set(idemKey, COMPLETED, response, TTL=1h)

                Note over Facade,Lock: 9) Lock Release
                Facade->>Lock: unlock("crew:" + crewId)
                Lock-->>Facade: unlocked ✅

                Facade-->>Controller: CrewJoinResponse
                Controller-->>Client: 201 Created
            end

        else Lock 획득 실패 (다른 사용자가 처리 중)
            Lock-->>Facade: false ❌

            Note over Facade: Wait (Exponential Backoff + Jitter)

            loop 최대 5회 재시도
                Facade->>Facade: sleep(100ms × 2^attempt + jitter)
                Facade->>Lock: tryLock("crew:" + crewId)

                alt 재시도 성공
                    Lock-->>Facade: true
                    Note over Facade: 위 로직 반복...
                else 재시도 실패
                    Lock-->>Facade: false
                end
            end

            alt 최종 실패 (5회 재시도 후)
                Facade->>IdemStore: delete(idemKey)
                Facade-->>Controller: 429 Too Many Requests
                Controller-->>Client: 잠시 후 다시 시도해주세요
            end
        end
    end
```

## 3. 동시성 제어 전략

크루 가입은 **3중 보호**로 동시성을 제어한다.

| 계층 | 방식 | 목적 |
|------|------|------|
| 1층 | Idempotency Key | 동일 요청 중복 방지 |
| 2층 | Distributed Lock (Redis) | 동시 요청 직렬화 |
| 3층 | Pessimistic Lock (DB) | 정원 정합성 보장 |

**왜 3중인가?**
- Idempotency: 같은 사용자가 버튼 연타하는 경우
- Distributed Lock: 서로 다른 사용자가 동시에 참여하는 경우
- Pessimistic Lock: 분산 락 없이도 DB 레벨에서 최종 방어
