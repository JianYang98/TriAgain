Feature: FCM 토큰 관리
  사용자가 앱 실행 시 FCM 토큰을 등록/갱신한다.

  Background:
    Given 사용자 "user_001"이 로그인되어 있다

  # ===== Happy Path =====

  Scenario: FCM 토큰 등록 성공
    When "user_001"이 FCM 토큰 "fcm-token-abc123"을 등록한다
    Then 응답 코드는 200이다

  Scenario: FCM 토큰 갱신 성공
    Given "user_001"이 FCM 토큰 "old-token"을 등록한다
    When "user_001"이 FCM 토큰 "new-token-xyz"을 등록한다
    Then 응답 코드는 200이다

  # ===== 실패 케이스 =====

  Scenario: 미인증 사용자가 FCM 토큰 등록 시 실패한다
    When 미인증 사용자가 FCM 토큰을 등록한다
    Then 응답 코드는 401이다
