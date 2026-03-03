Feature: 크루 상세 조회
  크루 멤버가 크루의 상세 정보를 조회한다.

  Background:
    Given 사용자 "leader_001"이 크루를 생성했다

  # ===== Happy Path =====

  Scenario: 크루 상세 정보를 조회한다
    Given 사용자 "user_002"가 크루에 참여했다
    When "user_002"가 크루 상세를 조회한다
    Then 응답 코드는 200이다
    And 응답에 name이 존재한다
    And 응답에 goal이 존재한다
    And 응답에 startDate가 존재한다
    And 응답에 endDate가 존재한다
    And 응답에 members가 존재한다

  Scenario: 크루 상세에 멤버별 프로필과 챌린지 진행도가 포함된다
    Given 사용자 "user_002"가 크루에 참여했다
    And 크루가 활성화되어 챌린지가 시작되었다
    When "user_002"가 크루 상세를 조회한다
    Then 응답 코드는 200이다
    And 멤버 "user_002"의 닉네임이 존재한다
    And 멤버 "user_002"의 챌린지 진행도가 존재한다
    And 멤버 "user_002"의 챌린지 상태는 "IN_PROGRESS"이다
    And 멤버 "user_002"의 완료일수는 0이다
    And 멤버 "user_002"의 목표일수는 3이다

  # ===== 실패 케이스 =====

  Scenario: 존재하지 않는 크루 조회 시 실패
    Given 사용자 "user_002"가 로그인되어 있다
    When "user_002"가 존재하지 않는 크루를 조회한다
    Then 응답 코드는 404이다
    And 에러 코드는 "CREW_NOT_FOUND"이다

  Scenario: 미참여 크루 조회 시 실패
    Given 사용자 "user_002"가 로그인되어 있다
    When "user_002"가 크루 상세를 조회한다
    Then 응답 코드는 403이다
    And 에러 코드는 "CREW_ACCESS_DENIED"이다
