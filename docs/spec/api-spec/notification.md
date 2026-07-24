# API 명세 — 알림 (Notification)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 API 계약 정본이다. 코드보다 이 문서를 먼저 수정한다.

---

### PATCH /users/me/fcm-token (FCM 토큰 등록/갱신)

앱 실행/로그인 시 클라이언트가 FCM 디바이스 토큰을 서버에 등록/갱신한다.

**요청 (Request)**
```
PATCH /users/me/fcm-token HTTP/1.1
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "fcmToken": "dK1x...FCM디바이스토큰"
}
```

**필드 설명:**
- `fcmToken`: (필수) Firebase Cloud Messaging 디바이스 토큰

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 400 | C001 | 잘못된 입력값입니다. |
| 401 | A003 | 인증이 필요합니다. |

---

### GET /notifications (내 알림 목록 조회)

내 알림을 최신순으로 페이지네이션 조회한다.

**요청 (Request)**
```
GET /notifications?isRead=false&page=0&size=20 HTTP/1.1
Authorization: Bearer <token>
```

**쿼리 파라미터:**
- `isRead`: (선택) 읽음 필터 — 미전달 시 전체, `false`: 안 읽은 알림만, `true`: 읽은 알림만
- `page`: (선택) 페이지 번호 (기본값 0)
- `size`: (선택) 페이지 크기 (기본값 20, 최대 50)

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": "notif_123",
        "type": "CREW_STARTED",
        "title": "크루 시작!",
        "content": "새벽 러닝 크루가 시작되었습니다.",
        "isRead": false,
        "targetType": "CREW",
        "targetId": "crew_123",
        "createdAt": "2026-03-20T09:00:00"
      }
    ],
    "hasNext": false
  },
  "error": null
}
```

**필드 설명:**
- `notifications`: 알림 목록 (최신순 정렬)
  - `id`: 알림 ID
  - `type`: 알림 타입 (CREW_STARTED, REMINDER 등)
  - `title`: 알림 제목
  - `content`: 알림 내용
  - `isRead`: 읽음 여부
  - `targetType`: 알림 대상 타입 (CREW 등)
  - `targetId`: 알림 대상 ID (nullable)
  - `createdAt`: 알림 생성 시각
- `hasNext`: 다음 페이지 존재 여부

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### GET /notifications/unread-count (안 읽은 알림 수 조회)

읽지 않은 알림 수를 조회한다. 뱃지 표시용.

**요청 (Request)**
```
GET /notifications/unread-count HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": {
    "count": 5
  },
  "error": null
}
```

**필드 설명:**
- `count`: 안 읽은 알림 수

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### PATCH /notifications/{id}/read (알림 읽음 처리)

알림을 읽음 상태로 변경한다. 본인 알림만 처리 가능.

**요청 (Request)**
```
PATCH /notifications/{id}/read HTTP/1.1
Authorization: Bearer <token>
```

**경로 파라미터:**
- `id`: (필수) 알림 ID

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 | 설명 |
|------|------|--------|------|
| 401 | A003 | 인증이 필요합니다. | 미인증 |
| 404 | S001 | 알림을 찾을 수 없습니다. | 존재하지 않거나 본인 알림 아님 |

---

### DELETE /notifications (알림 전체 삭제)

본인의 알림을 전체 삭제한다. Hard Delete. 0건이어도 200 OK (멱등).

**요청 (Request)**
```
DELETE /notifications HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

### PATCH /notifications/read-all (알림 전체 읽음)

본인의 안 읽은 알림을 전체 읽음 처리한다. 이미 전부 읽은 상태여도 200 OK (멱등).

**요청 (Request)**
```
PATCH /notifications/read-all HTTP/1.1
Authorization: Bearer <token>
```

**성공 응답 (200 OK)**
```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**에러 응답**
| HTTP | 코드 | 메시지 |
|------|------|--------|
| 401 | A003 | 인증이 필요합니다. |

---

