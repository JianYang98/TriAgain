Feature: 마이페이지
  사용자가 자신의 프로필을 조회하고 닉네임을 변경한다.

  # ===== 내 프로필 조회 =====

  Scenario: 내 프로필을 조회한다
    Given 사용자 "user_001"이 로그인되어 있다
    When "user_001"이 내 프로필을 조회한다
    Then 응답 코드는 200이다
    And 응답에 id가 존재한다
    And 응답에 nickname이 존재한다

  Scenario: 미인증 상태로 프로필 조회 시 실패
    When 미인증 상태로 프로필을 조회한다
    Then 응답 코드는 401이다

  # ===== 닉네임 변경 =====

  Scenario: 닉네임을 변경한다
    Given 사용자 "user_001"이 로그인되어 있다
    When "user_001"이 닉네임을 "새닉네임"으로 변경한다
    Then 응답 코드는 200이다
    And 응답의 닉네임은 "새닉네임"이다

  Scenario: 빈 닉네임으로 변경 시 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When "user_001"이 닉네임을 ""으로 변경한다
    Then 응답 코드는 400이다

  Scenario: 패턴 불일치 닉네임으로 변경 시 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When "user_001"이 닉네임을 "a"으로 변경한다
    Then 응답 코드는 400이다
    And 에러 코드는 "INVALID_NICKNAME"이다

  Scenario: 특수문자 닉네임으로 변경 시 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When "user_001"이 닉네임을 "!@#$"으로 변경한다
    Then 응답 코드는 400이다
    And 에러 코드는 "INVALID_NICKNAME"이다

  Scenario: 미인증 상태로 닉네임 변경 시 실패
    When 미인증 상태로 닉네임을 변경한다
    Then 응답 코드는 401이다

  # ===== 로그아웃 =====

  Scenario: 로그아웃한다
    When 로그아웃을 요청한다
    Then 응답 코드는 200이다