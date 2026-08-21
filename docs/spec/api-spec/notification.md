# API 명세 — 알림 (Notification)

> 전체 인덱스: [`../api-spec.md`](../api-spec.md) · 이 문서가 알림 조회·읽음·삭제 API 계약의 정본이다.
>
> FCM 토큰 등록 계약은 User API다. [`auth-user.md`](./auth-user.md)의
> `PATCH /users/me/fcm-token`을 정본으로 사용하며 이 문서에 중복 정의하지 않는다.

---

## 1. 현재 범위

알림 기능은 서로 다른 두 결과를 다룬다.

| 결과 | 저장 위치 | 사용자 확인 방법 | 실패 영향 |
|---|---|---|---|
| 인앱 알림 | PostgreSQL `notifications` | 이 문서의 조회 API | 저장 실패 시 목록에 나타나지 않음 |
| 푸시 알림 | Firebase Cloud Messaging | OS 푸시 | 전송 실패가 이미 저장된 인앱 알림을 취소하지 않음 |

- 이 문서의 5개 `/notifications` API는 인앱 알림만 조회·변경한다.
- 알림 조회·변경 API 호출 자체는 FCM을 전송하지 않는다.
- 모든 엔드포인트에 유효한 Access Token이 필요하다.
- 인증 실패 응답은 `401 A003` (`인증이 필요합니다.`)이다.

### 공통 응답 형식

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

---

## 2. 알림 타입과 현재 생성 상태

`type`이 가질 수 있는 전체 값은 다음과 같다.

| `type` | 현재 자동 생성 | 생성 계기 |
|---|---:|---|
| `CREW_STARTED` | 예 | 매일 09:00, 오늘 시작한 ACTIVE 크루의 전체 멤버 |
| `REMINDER` | 예 | 매 15분, 마감 15분 이상 30분 미만 전인 미인증 멤버 |
| `CREW_FIRST_VERIFICATION` | 조건부 | 오늘 크루 첫 인증 커밋 후, 첫 인증자를 제외한 ACTIVE 크루원 |
| `CHALLENGE_SUCCESS` | 아니오 | 이벤트는 발행되지만 리스너 메서드가 주석 처리됨 |
| `CHALLENGE_FAILED` | 아니오 | 실패 전이는 수행하지만 알림 Port 호출이 주석 처리됨 |
| `VERIFICATION_APPROVED` | 아니오 | enum·DB 허용값만 존재 |
| `VERIFICATION_REJECTED` | 아니오 | enum·DB 허용값만 존재 |
| `CREW_INVITE` | 아니오 | enum·DB 허용값만 존재 |
| `REPORT_RECEIVED` | 아니오 | enum·DB 허용값만 존재 |
| `REVIEW_COMPLETED` | 아니오 | enum·DB 허용값만 존재 |
| `UPLOAD_COMPLETED` | 아니오 | enum·DB 허용값만 존재 |

`CREW_FIRST_VERIFICATION`은
`notification.crew-first-verification.enabled=true`일 때만 리스너가 등록된다.
운영 설정의 기본값은 `true`이고, 다른 프로필은 값을 명시하지 않으면 비활성이다.
알림 허용 시간은 서버 시계 기준 `[08:00, 22:00)`이다.

> **제품 규칙 확정 예정:** 위 시각과 활성 상태는 현재 서버 동작을 기록한 값이다.
> 사용자별 알림 ON/OFF, 알림 종류별 수신 여부, 방해 금지 시간과 발송 시점은 추후 제품 결정으로
> 확정할 예정이다. 확정 전까지 성공·실패 알림을 임의로 활성화하거나 현재 시간을 최종 정책으로
> 간주하지 않는다.

`targetType`의 허용값은 `CREW`, `VERIFICATION`, `CHALLENGE`다. 현재 자동 생성되는 알림은
모두 `CREW`와 크루 ID를 사용한다. 타겟 없는 알림 모델도 허용하므로 `targetType`과
`targetId`는 응답에서 `null`일 수 있다.

---

## 3. GET /notifications

내 알림을 생성 시각 내림차순으로 조회한다. 전체 개수 대신 `hasNext`만 반환하는 Slice 방식이다.

**요청**

```http
GET /notifications?isRead=false&page=0&size=20 HTTP/1.1
Authorization: Bearer <access-token>
```

### 쿼리 파라미터

| 이름 | 타입 | 필수 | 기본값 | 처리 규칙 |
|---|---|---:|---:|---|
| `isRead` | boolean | 아니오 | `null` | 미전달은 전체, `false`는 미읽음, `true`는 읽음 |
| `page` | integer | 아니오 | `0` | 음수는 `0`으로 보정 |
| `size` | integer | 아니오 | `20` | `1..50`만 사용하며, 그 밖의 값은 `20`으로 보정 |

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": "NTFY0123456789ABCDEFGHJKMNPQ",
        "type": "CREW_STARTED",
        "title": "새벽 러닝 | 크루 시작!",
        "content": "오늘부터 3일! 함께 시작해요",
        "isRead": false,
        "targetType": "CREW",
        "targetId": "CREW0123456789ABCDEFGHJKMNP",
        "createdAt": "2026-08-20T09:00:00"
      }
    ],
    "hasNext": false
  },
  "error": null
}
```

### 응답 필드

| 필드 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `notifications` | array | 아니오 | 알림 목록. 결과가 없으면 `[]` |
| `notifications[].id` | string | 아니오 | 알림 ID |
| `notifications[].type` | string | 아니오 | 2절의 알림 타입 |
| `notifications[].title` | string | 아니오 | 알림 제목 |
| `notifications[].content` | string | 아니오 | 알림 본문 |
| `notifications[].isRead` | boolean | 아니오 | 읽음 여부 |
| `notifications[].targetType` | string | 예 | `CREW`, `VERIFICATION`, `CHALLENGE` |
| `notifications[].targetId` | string | 예 | 연결 대상 ID |
| `notifications[].createdAt` | string(date-time) | 아니오 | 서버에서 알림을 생성한 시각 |
| `hasNext` | boolean | 아니오 | 다음 Slice 존재 여부 |

### 에러 응답

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 미인증 또는 유효하지 않은 Access Token |

> **현행 입력 처리 공백:** `page=abc`처럼 정수로 변환할 수 없는 값과 잘못된 boolean 값은
> 별도 타입 변환 예외 핸들러가 없어 `500 C002`로 귀결될 수 있다. 이는 원하는 계약으로
> 확정한 것이 아니라 현재 코드에서 확인한 차이다.

---

## 4. GET /notifications/unread-count

내가 읽지 않은 알림 수를 조회한다. 클라이언트 뱃지 표시에 사용할 수 있다.

**요청**

```http
GET /notifications/unread-count HTTP/1.1
Authorization: Bearer <access-token>
```

**성공 응답 — 200 OK**

```json
{
  "success": true,
  "data": {
    "count": 5
  },
  "error": null
}
```

| 필드 | 타입 | nullable | 설명 |
|---|---|---:|---|
| `count` | integer(int64) | 아니오 | 미읽음 알림 수. 없으면 `0` |

---

## 5. PATCH /notifications/{id}/read

내 알림 하나를 읽음 처리한다. 이미 읽은 알림을 다시 처리해도 `200 OK`다.

**요청**

```http
PATCH /notifications/NTFY0123456789ABCDEFGHJKMNPQ/read HTTP/1.1
Authorization: Bearer <access-token>
```

| 경로 변수 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `id` | string | 예 | 알림 ID |

**성공 응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 미인증 또는 유효하지 않은 Access Token |
| 404 | S001 | 알림이 없거나 다른 사용자의 알림 |

다른 사용자의 알림 존재 여부를 노출하지 않기 위해 두 경우 모두 `S001`을 반환한다.

---

## 6. DELETE /notifications

내 알림을 전부 Hard Delete한다. 대상이 0건이어도 `200 OK`다.

**요청**

```http
DELETE /notifications HTTP/1.1
Authorization: Bearer <access-token>
```

**성공 응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

삭제한 알림은 복구 API가 없다. 이 요청은 FCM 토큰과 사용자 계정을 변경하지 않는다.

---

## 7. PATCH /notifications/read-all

내 미읽음 알림을 전부 읽음 처리한다. 대상이 0건이어도 `200 OK`다.

**요청**

```http
PATCH /notifications/read-all HTTP/1.1
Authorization: Bearer <access-token>
```

**성공 응답 — 200 OK**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

## 8. 생성·전송 운영 규칙

### 인앱 저장과 FCM

1. 생성 작업은 먼저 인앱 알림을 저장한다.
2. FCM 토큰이 있는 사용자에게만 푸시를 시도한다.
3. `firebase.enabled=true`이면 Firebase를 사용하고, 그렇지 않으면 No-op 어댑터가 성공으로 응답한다.
4. Firebase의 일시 오류는 최대 3회 시도한다(1초부터 2배 backoff).
5. `UNREGISTERED` 또는 `INVALID_ARGUMENT`이면 토큰을 영구 무효로 보고 저장된 FCM 토큰을 지운다.
6. 푸시 오류는 생성 작업에서 잡아 로그로 남긴다. 이미 저장된 인앱 알림은 유지한다.

### 실패 격리와 중복 경계

- `CREW_STARTED`와 `REMINDER`의 인앱 저장은 건별 처리하고, 실패 건은 Dead Letter에 기록한다.
- 두 스케줄러의 FCM 전송은 인앱 저장 성공 건에 대해서만 트랜잭션 밖에서 수행한다.
- `CREW_FIRST_VERIFICATION`은 커밋 후 비동기로 실행하고 수신자별 오류를 격리한다.
- 첫 인증 알림은 같은 크루·같은 날짜의 기존 알림 존재 여부를 먼저 확인한다.
- `notifications`에는 업무키 유니크 제약이 없다. 따라서 스케줄러 중복 실행, 다중 인스턴스의
  동시 실행 또는 첫 인증의 동시 경합까지 DB가 중복 생성을 막아주지는 않는다.
- 오래된 알림 삭제용 저장소 메서드는 있으나 이를 호출하는 보관 기간 스케줄러는 아직 없다.

### 설정 확인 범위

- 운영 코드의 기본값은 `FIREBASE_ENABLED=false`, `CREW_FIRST_VERIFICATION_ENABLED=true`다.
- 실제 배포 환경 변수, Firebase 서비스 계정 파일 배치, Dead Letter 후속 처리·경보는
  저장소 밖 운영 설정을 함께 확인해야 한다.
