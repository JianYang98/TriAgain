Feature: 크루 피드 조회
  크루원들의 인증 목록과 나의 현황을 조회한다.

  Background:
    Given 사용자 "user_001"이 크루 "운동 크루"에 참여 중이다
    And 크루 "운동 크루"가 활성 상태이다

  # ===== Happy Path =====

  Scenario: 크루 피드를 조회한다
    Given "user_002"가 크루 "운동 크루"에 참여 중이다
    And "user_002"가 오늘 인증을 완료했다
    When "user_001"이 크루 "운동 크루"의 피드를 조회한다
    Then 응답 코드는 200이다
    And 피드에 1개의 인증이 포함된다
    And 응답에 myProgress가 존재한다

  Scenario: 나의 현황에 챌린지 진행 상태가 포함된다
    Given "user_001"의 챌린지가 진행 중이다
    And "user_001"이 1일차 인증을 완료했다
    When "user_001"이 크루 "운동 크루"의 피드를 조회한다
    Then 응답 코드는 200이다
    And myProgress의 completedDays는 1이다
    And myProgress의 status는 "IN_PROGRESS"이다

  Scenario: 인증이 없으면 빈 피드를 반환한다
    When "user_001"이 크루 "운동 크루"의 피드를 조회한다
    Then 응답 코드는 200이다
    And 피드에 0개의 인증이 포함된다

  Scenario: 피드는 최신순으로 정렬된다
    Given "user_002"가 크루 "운동 크루"에 참여 중이다
    And "user_002"가 어제 인증을 완료했다
    And "user_002"가 오늘 인증을 완료했다
    When "user_001"이 크루 "운동 크루"의 피드를 조회한다
    Then 응답 코드는 200이다
    And 피드의 첫 번째 인증이 오늘 날짜이다

  Scenario: 피드를 페이지네이션으로 조회한다
    Given 크루 "운동 크루"에 25개의 인증이 존재한다
    When "user_001"이 크루 "운동 크루"의 피드를 page 0으로 조회한다
    Then 응답 코드는 200이다
    And 피드에 20개의 인증이 포함된다
    And 응답에 hasNext가 true이다

  # ===== 실패 케이스 =====

  Scenario: 존재하지 않는 크루 피드 조회 시 실패
    When "user_001"이 존재하지 않는 크루의 피드를 조회한다
    Then 응답 코드는 404이다
    And 에러 코드는 "CREW_NOT_FOUND"이다

  Scenario: 미참여 크루 피드 조회 시 실패
    Given 크루 "독서 크루"가 존재한다
    And "user_001"은 크루 "독서 크루"에 참여하지 않았다
    When "user_001"이 크루 "독서 크루"의 피드를 조회한다
    Then 응답 코드는 403이다
    And 에러 코드는 "CREW_ACCESS_DENIED"이다
