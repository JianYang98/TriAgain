# Schema - ERD 및 데이터 설계

## 1. ERD (Entity Relationship Diagram)

```mermaid
erDiagram
    users ||--o{ crew_members : "참여"
    crews ||--o{ crew_members : "보유"
    
    crews ||--o{ challenges : "생성"
    
    challenges ||--o{ verifications : "포함"
    verifications |o--o| upload_session : "0..1"

    users ||--o{ verifications : "작성"
    
    verifications ||--o{ reports : "신고됨"
    users ||--o{ reports : "신고함"
    
    reports ||--o{ reviews : "검토됨"
    users ||--o{ reviews : "검토함"
    
    users ||--o{ notifications : "받음"
    
    verifications ||--o{ reactions : "반응"
    users ||--o{ reactions : "남김"

    users {
        string id PK "카카오 고유 ID (String 변환)"
        string provider "KAKAO"
        string email "nullable"
        string nickname
        string profile_image_url
        timestamp created_at
    }
    
    crews {
        string id PK
        string creator_id FK
        string name
        string goal
        enum verification_type "TEXT / PHOTO"
        boolean allow_late_join "크루장이 중간 가입 허용 여부 설정"
        int min_members "DEFAULT 1"
        int max_members
        int current_members
        enum status
        date start_date
        date end_date
        string invite_code UK
        timestamp created_at
    }
    
    crew_members {
        string id PK
        string user_id FK
        string crew_id FK
        enum role
        timestamp joined_at
    }
    
    challenges {
        string id PK
        string user_id FK
        string crew_id FK
        int cycle_number
        int target_days
        int completed_days
        enum status
        date start_date
        timestamp deadline
        timestamp created_at
    }
    
    verifications {
        string id PK
        string challenge_id FK
        string user_id FK
        string crew_id FK
        string upload_session_id FK "nullable, 사진 인증 시에만"
        string image_url
        string text_content
        enum status
        int report_count
        date target_date
        int attempt_number
        enum review_status
        timestamp created_at
    }
    
    reports {
        string id PK
        string verification_id FK
        string reporter_id FK
        enum reason
        enum status
        string description
        timestamp created_at
    }
    
    reviews {
        string id PK
        string report_id FK
        string reviewer_id FK
        enum reviewer_type
        enum decision
        string comment
        timestamp created_at
    }
    
    notifications {
        string id PK
        string user_id FK
        enum type
        string title
        string content
        boolean is_read
        timestamp created_at
    }
    
    reactions {
        string id PK
        string verification_id FK
        string user_id FK
        string emoji
        timestamp created_at
    }
    
    upload_session {
        bigint id PK
        bigint user_id FK
        varchar image_key
        varchar content_type
        varchar status "PENDING / COMPLETED / EXPIRED"
        timestamp requested_at
        timestamp created_at
    }
```

## 2. 주요 관계 설명

| 관계 | 설명 |
|------|------|
| users ↔ crew_members | 유저가 여러 크루에 참여 가능 |
| crews ↔ crew_members | 크루가 여러 멤버 보유 |
| crews ↔ challenges | 크루 내 여러 챌린지 사이클 |
| challenges ↔ verifications | 챌린지당 여러 인증 기록 |
| verifications ↔ upload_session | 사진 인증 시에만 0..1 관계 (nullable FK) |
| verifications ↔ reports | 인증에 대한 신고 |
| reports ↔ reviews | 신고에 대한 검토 |

## 3. 상태(Enum) 정의

### crews.status
| 값 | 의미 |
|----|------|
| RECRUITING | 모집 중 |
| ACTIVE | 진행 중 |
| COMPLETED | 완료 |

### crews.verification_type
| 값 | 의미 |
|----|------|
| TEXT | 텍스트 인증 (텍스트 필수) |
| PHOTO | 사진 인증 (사진 필수 + 텍스트 선택) |

### crew_members.role
| 값 | 의미 |
|----|------|
| LEADER | 크루장 |
| MEMBER | 일반 멤버 |

### challenges.status
| 값 | 의미 |
|----|------|
| IN_PROGRESS | 진행 중 |
| SUCCESS | 3일 연속 성공 |
| FAILED | 실패 (재시작 가능) |
| ENDED | 크루 기간 종료로 인한 챌린지 종료 |

### verifications.status
| 값 | 의미 |
|----|------|
| APPROVED | 정상 인증 (기본값) |
| REPORTED | 신고 접수됨 (3건 이상) |
| HIDDEN | 검토 중 숨김 처리 |
| REJECTED | 검토 후 반려됨 |

### verifications.review_status
| 값 | 의미 |
|----|------|
| NOT_REQUIRED | 검토 불필요 (신고 없음) |
| PENDING | 검토 대기 (신고 3건) |
| IN_REVIEW | 검토 중 |
| COMPLETED | 검토 완료 |

### upload_session.status
| 값 | 의미 |
|----|------|
| PENDING | presignedUrl 발급, S3 업로드 대기 |
| COMPLETED | S3 업로드 완료 (verification 생성 가능) |
| EXPIRED | 시간 초과 / 만료 |

### reports.reason
| 값 | 의미 |
|----|------|
| SPAM | 스팸/도배 |
| INAPPROPRIATE | 부적절한 내용 |
| FAKE | 거짓 인증 |
| COPYRIGHT | 저작권 침해 |
| OTHER | 기타 |

### reports.status
| 값 | 의미 |
|----|------|
| PENDING | 검토 대기 |
| APPROVED | 승인 (조치 완료) |
| REJECTED | 기각 |
| EXPIRED | 7일 미검토 자동 승인 |

### reviews.reviewer_type
| 값 | 의미 |
|----|------|
| AUTO | 자동 (신고 3건) |
| CREW_LEADER | 크루장 |
| AI | AI 검토 (Phase 2+) |
| ADMIN | 관리자 (Phase 3+) |

### reviews.decision
| 값 | 의미 |
|----|------|
| APPROVE | 승인 (문제 없음) |
| REJECT | 반려 (부적절) |
| PENDING | 보류 (추가 검토 필요) |

### notifications.type
| 값 | 의미 |
|----|------|
| VERIFICATION_APPROVED | 인증 승인 |
| VERIFICATION_REJECTED | 인증 반려 |
| CHALLENGE_SUCCESS | 챌린지 성공 |
| CHALLENGE_FAILED | 챌린지 실패 |
| CREW_INVITE | 크루 초대 |
| REPORT_RECEIVED | 신고 접수 |
| REVIEW_COMPLETED | 검토 완료 |
| UPLOAD_COMPLETED | 이미지 업로드 완료 |

## 4. 인덱스 설계

### 핵심 인덱스

```sql
-- 크루 피드 조회 (인증 목록)
CREATE INDEX idx_verification_crew_verified 
ON verification(crew_id, created_at DESC);

-- 중복 인증 방지
CREATE UNIQUE INDEX idx_verification_unique
ON verification(user_id, crew_id, target_date);

-- 신고 중복 방지
CREATE UNIQUE INDEX idx_report_unique
ON report(verification_id, reporter_id);
```

### Moderation 관련 인덱스

```sql
-- 신고 횟수 조회 (3건 → REPORTED)
CREATE INDEX idx_verification_report_count
ON verification(report_count) 
WHERE report_count >= 3;

-- 검토 대기 목록 조회
CREATE INDEX idx_verification_review_status
ON verification(review_status, created_at DESC)
WHERE review_status = 'PENDING';

-- 검토자별 검토 이력
CREATE INDEX idx_review_reviewer
ON review(reviewer_id, created_at DESC);

-- 신고 상태별 조회
CREATE INDEX idx_report_status
ON report(status, created_at DESC)
WHERE status = 'PENDING';
```

## 5. 설계 트레이드오프: Verification의 3-way FK

Verification이 User, Crew, Challenge를 직접 참조하는 구조를 선택했다.

### 선택 이유

**유연성 관점:**
- 낮은 결합도 (조회 경로 분산)
- 독립적인 도메인 유지
- 변경 영향도 최소화

**성능 관점:**
- 단일 테이블 조회 가능
- JOIN 최소화
- 인덱스 최적화 용이

### 대안 (Crew → Challenge → Verification 계층 구조)
- 인증이 챌린지에 종속
- JOIN 필수 (복잡도 증가)
- 조회 경로 제한

### 결론
3-way FK 구조를 유지하되, Moderation 추가에 따른 인덱스 보강으로 성능을 보완한다.
