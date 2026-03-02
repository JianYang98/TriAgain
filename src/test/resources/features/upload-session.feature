Feature: 업로드 세션 생성
  사진 인증 시 S3 Presigned URL을 발급받는다.

  Background:
    Given 사용자 "user_001"이 크루 "운동 크루"에 참여 중이다
    And 크루 "운동 크루"의 인증방식이 "PHOTO"이다

  # ===== Happy Path =====

  Scenario: 업로드 세션 생성 성공
    When 다음 정보로 업로드 세션을 생성한다
      | fileName | verification_image.jpg |
      | fileType | image/jpeg             |
      | fileSize | 2048576                |
    Then 응답 코드는 201이다
    And 응답에 uploadSessionId가 존재한다
    And 응답에 presignedUrl이 존재한다
    And 응답에 imageUrl이 존재한다
    And 응답에 expiresAt이 존재한다

  # ===== 실패 케이스 =====

  Scenario: 지원하지 않는 파일 형식으로 실패
    When 파일 타입 "image/gif"로 업로드 세션을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "INVALID_FILE_TYPE"이다

  Scenario: 파일 크기 초과로 실패
    When 파일 크기 10485760으로 업로드 세션을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "FILE_TOO_LARGE"이다

  Scenario: 마감 시간 이후 업로드 세션 생성 실패
    Given 인증 마감 시간이 지났다
    When 업로드 세션을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "VERIFICATION_DEADLINE_EXCEEDED"이다

  # ===== 세션 만료 =====

  Scenario: PENDING 상태 세션이 15분 경과 시 만료 처리
    Given 업로드 세션이 15분 전에 생성되었다
    And 세션 상태가 "PENDING"이다
    When 만료 스케줄러가 실행된다
    Then 세션 상태는 "EXPIRED"이다

  # ===== Lambda 콜백 (Internal API) =====

  Scenario: Lambda 콜백으로 세션이 COMPLETED 처리된다
    Given 업로드 세션이 생성되어 PENDING 상태이다
    When Lambda가 업로드 완료를 알린다
    Then 세션 상태는 "COMPLETED"이다

  Scenario: 이미 완료된 세션에 Lambda가 다시 콜백하면 무시된다
    Given 업로드 세션이 이미 "COMPLETED" 상태이다
    When Lambda가 업로드 완료를 알린다
    Then 세션 상태는 "COMPLETED"이다

  Scenario: 업로드 완료 시 SSE로 알림을 받는다
    Given 업로드 세션이 생성되어 PENDING 상태이다
    And SSE로 세션 이벤트를 구독 중이다
    When Lambda가 업로드 완료를 알린다
    Then SSE로 "COMPLETED" 이벤트를 수신한다
