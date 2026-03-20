Feature: 알림 조회 및 읽음 처리
  사용자가 인앱 알림을 조회하고 읽음 처리한다.

  Background:
    Given 사용자 "user_001"이 로그인되어 있다

  # ===== Happy Path =====

  Scenario: 알림이 없을 때 빈 목록을 반환한다
    When "user_001"이 알림 목록을 조회한다
    Then 응답 코드는 200이다
    And 알림 목록 개수는 0이다

  Scenario: 알림이 존재하면 최신순으로 반환한다
    Given "user_001"에게 알림 2개가 존재한다
    When "user_001"이 알림 목록을 조회한다
    Then 응답 코드는 200이다
    And 알림 목록 개수는 2이다

  Scenario: 안 읽은 알림 수를 조회한다
    Given "user_001"에게 알림 2개가 존재한다
    When "user_001"이 안 읽은 알림 수를 조회한다
    Then 응답 코드는 200이다
    And 안 읽은 알림 수는 2이다

  Scenario: 알림 읽음 처리 후 안 읽은 수가 감소한다
    Given "user_001"에게 알림 2개가 존재한다
    When "user_001"이 첫 번째 알림을 읽음 처리한다
    Then 응답 코드는 200이다
    When "user_001"이 안 읽은 알림 수를 조회한다
    And 안 읽은 알림 수는 1이다

  # ===== 실패 케이스 =====

  Scenario: 존재하지 않는 알림 읽음 처리 시 실패한다
    When "user_001"이 존재하지 않는 알림을 읽음 처리한다
    Then 응답 코드는 404이다
    And 에러 코드는 "NOTIFICATION_NOT_FOUND"이다

  Scenario: 타인의 알림 읽음 처리 시 실패한다
    Given 사용자 "user_002"가 로그인되어 있다
    And "user_002"에게 알림 1개가 존재한다
    When "user_001"이 "user_002"의 알림을 읽음 처리한다
    Then 응답 코드는 404이다
    And 에러 코드는 "NOTIFICATION_NOT_FOUND"이다
