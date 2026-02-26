Feature: 인증 생성
  크루원이 하루 1번 텍스트 또는 사진으로 인증한다.

  Background:
    Given 사용자 "user_001"이 크루 "운동 크루"에 참여 중이다
    And "user_001"의 챌린지가 진행 중이다

  # ===== Happy Path =====

  Scenario: 텍스트 인증 성공
    Given 크루 "운동 크루"의 인증방식이 "TEXT"이다
    When 다음 정보로 인증을 생성한다
      | textContent | 오늘도 30분 달리기 완료! |
    Then 응답 코드는 201이다
    And 인증 상태는 "APPROVED"이다
    And 챌린지 completed_days가 1 증가한다

  Scenario: 사진 인증 성공
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    And 업로드 세션이 완료되었다
    When 다음 정보로 인증을 생성한다
      | uploadSessionId | upload_123              |
      | imageUrl        | https://s3.../image.jpg |
      | textContent     | 오늘도 운동 완료!       |
    Then 응답 코드는 201이다
    And 인증 상태는 "APPROVED"이다

  Scenario: 사진 인증에서 텍스트는 선택이다
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    And 업로드 세션이 완료되었다
    When 텍스트 없이 사진만으로 인증을 생성한다
    Then 응답 코드는 201이다

  # ===== 실패 케이스 =====

  Scenario: 하루에 2번 인증 시도 시 실패
    Given "user_001"이 오늘 이미 인증을 완료했다
    When 다시 인증을 생성한다
    Then 응답 코드는 409이다
    And 에러 코드는 "VERIFICATION_ALREADY_EXISTS"이다

  Scenario: 마감 시간 이후 인증 실패
    Given 인증 마감 시간이 지났다
    When 인증을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "VERIFICATION_DEADLINE_EXCEEDED"이다

  Scenario: 사진 필수 크루에서 사진 없이 인증 실패
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    When 사진 없이 텍스트만으로 인증을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "PHOTO_REQUIRED"이다

  Scenario: 크루 멤버가 아닌 사용자 인증 실패
    # Background와 독립적 시나리오
    Given 사용자 "user_999"가 로그인되어 있다
    And "user_999"는 크루 "운동 크루"에 참여하지 않았다
    When 인증을 생성한다
    Then 응답 코드는 403이다
    And 에러 코드는 "CREW_ACCESS_DENIED"이다

  Scenario: 만료된 업로드 세션으로 인증 실패
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    When 만료된 uploadSessionId로 인증을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "UPLOAD_SESSION_EXPIRED"이다

  # ===== Grace Period =====

  Scenario: 마감 시간 이후 Grace Period 내 인증 성공
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    And 업로드 세션이 마감 직전에 요청되었다
    And 마감 시간 이후 5분 이내이다
    When 인증을 생성한다
    Then 응답 코드는 201이다

  Scenario: Grace Period 이후 인증 실패
    Given 크루 "운동 크루"의 인증방식이 "PHOTO"이다
    And 업로드 세션 요청 시각이 마감 시간 이후 5분을 초과했다
    When 인증을 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "VERIFICATION_DEADLINE_EXCEEDED"이다
