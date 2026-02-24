Feature: 크루 참여
  사용자가 초대코드를 입력하여 크루에 참여한다.

  Background:
    Given 사용자 "leader_001"이 크루를 생성했다

  # ===== Happy Path =====

  Scenario: 초대코드로 크루에 참여한다
    Given 사용자 "user_002"가 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 201이다
    And 응답에 crewId가 존재한다
    And "user_002"의 역할은 "MEMBER"이다
    And 현재 인원은 2이다

  Scenario: 중간 가입 허용된 크루에 시작 후 참여 성공
    Given 중간 가입이 허용된 크루가 이미 시작되었다
    And 사용자 "user_002"가 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 201이다
    And "user_002"의 챌린지가 자동 생성된다

  # ===== 실패 케이스 =====

  Scenario: 존재하지 않는 초대코드로 참여 실패
    Given 사용자 "user_002"가 로그인되어 있다
    When 초대코드 "XXXXXX"로 크루 참여를 요청한다
    Then 응답 코드는 404이다
    And 에러 코드는 "INVALID_INVITE_CODE"이다

  Scenario: 정원이 가득 찬 크루에 참여 실패
    Given 크루 정원이 가득 찼다
    And 사용자 "user_999"가 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 409이다
    And 에러 코드는 "CREW_FULL"이다

  Scenario: 이미 참여한 크루에 중복 참여 실패
    Given 사용자 "user_002"가 크루에 참여했다
    When "user_002"가 같은 크루에 다시 참여를 요청한다
    Then 응답 코드는 409이다
    And 에러 코드는 "CREW_ALREADY_JOINED"이다

  Scenario: 중간 가입 불가 크루에 시작 후 참여 실패
    Given 중간 가입이 불가한 크루가 이미 시작되었다
    And 사용자 "user_002"가 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 400이다
    And 에러 코드는 "CREW_NOT_RECRUITING"이다

  Scenario: 종료 3일 전 이후에는 참여 실패
    Given 크루 종료일이 2일 남았다
    And 사용자 "user_002"가 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 400이다
    And 에러 코드는 "CREW_JOIN_DEADLINE_PASSED"이다
