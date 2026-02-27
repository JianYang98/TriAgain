@wip
Feature: 챌린지 자동 생성
  크루 시작 시 개인별 3일 챌린지가 자동으로 생성되고 관리된다.

  # ===== 자동 생성 =====

  Scenario: 크루 시작일에 멤버들의 챌린지가 자동 생성된다
    Given 크루 "운동 크루"의 시작일이 오늘이다
    And "leader_001"과 "user_002"가 크루에 참여 중이다
    When 크루가 시작된다
    Then 응답 코드는 200이다
    And "leader_001"의 챌린지가 자동 생성된다
    And "user_002"의 챌린지가 자동 생성된다
    And 챌린지 cycle_number는 1이다
    And 챌린지 target_days는 3이다
    And 챌린지 상태는 "IN_PROGRESS"이다

  Scenario: 중간 가입 시 챌린지가 즉시 생성된다
    Given 중간 가입이 허용된 크루가 이미 시작되었다
    And 사용자 "user_003"이 로그인되어 있다
    When 초대코드를 입력하여 크루에 참여한다
    Then 응답 코드는 201이다
    And "user_003"의 챌린지가 자동 생성된다
    And 챌린지 start_date는 오늘이다

  # ===== 3일 성공 =====

  Scenario: 3일 연속 인증 시 챌린지 성공
    Given 사용자 "user_001"이 챌린지를 진행 중이다
    When "user_001"이 3일 연속 인증을 완료한다
    Then 응답 코드는 200이다
    And 챌린지 상태는 "SUCCESS"이다
    And 새로운 챌린지가 자동 생성된다
    And 새 챌린지 cycle_number는 2이다

  # ===== 실패 후 재시작 =====

  Scenario: 인증 실패 시 챌린지 실패 후 새 챌린지 시작
    Given 사용자 "user_001"이 챌린지를 진행 중이다
    When "user_001"이 하루 인증을 놓친다
    Then 현재 챌린지 상태는 "FAILED"이다
    And 새로운 챌린지가 자동 생성된다
    And 새 챌린지 cycle_number는 2이다

  Scenario: 크루 종료 3일 남았을 때 실패하면 새 챌린지를 만든다
    Given 사용자 "user_001"이 챌린지를 진행 중이다
    And 크루 종료일이 3일 남았다
    When "user_001"이 하루 인증을 놓친다
    Then 현재 챌린지 상태는 "FAILED"이다
    And 새로운 챌린지가 자동 생성된다

  Scenario: 크루 종료 3일 미만 남았을 때 실패하면 새 챌린지 안 만든다
    Given 사용자 "user_001"이 챌린지를 진행 중이다
    And 크루 종료일이 2일 남았다
    When "user_001"이 하루 인증을 놓친다
    Then 현재 챌린지 상태는 "FAILED"이다
    And 새로운 챌린지는 생성되지 않는다

  # ===== 종료 =====

  Scenario: 크루 기간 종료 시 진행 중 챌린지도 종료된다
    Given 사용자 "user_001"이 챌린지를 진행 중이다
    When 크루 기간이 종료된다
    Then 챌린지 상태는 "ENDED"이다
    And 새로운 챌린지는 생성되지 않는다

  # ===== 작심삼일 표시 =====

  Scenario: 챌린지 1번 성공 시 작심삼일 1번째로 표시된다
    Given 사용자 "user_001"이 챌린지를 1번 성공했다
    When 크루 상세를 조회한다
    Then 응답 코드는 200이다
    And "user_001"의 작심삼일 횟수는 1이다

  Scenario: 챌린지 3번 성공 시 작심삼일 3번째로 표시된다
    Given 사용자 "user_001"이 챌린지를 3번 성공했다
    When 크루 상세를 조회한다
    Then 응답 코드는 200이다
    And "user_001"의 작심삼일 횟수는 3이다
