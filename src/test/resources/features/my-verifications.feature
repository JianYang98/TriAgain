Feature: 내 인증 현황 조회
  크루원이 자신의 인증 날짜, 연속 스트릭, 작심삼일 달성 횟수를 조회한다.

  Background:
    Given 사용자 "user_001"이 크루 "운동 크루"에 참여 중이다

  # ===== Happy Path =====

  Scenario: 인증 기록이 있으면 verifiedDates + streakCount + completedChallenges 반환
    Given "user_001"이 크루 "운동 크루"에 3일 연속 인증을 완료했다
    And "user_001"이 크루 "운동 크루"에 성공한 챌린지가 2개 있다
    When "user_001"이 크루 "운동 크루"의 내 인증 현황을 조회한다
    Then 응답 코드는 200이다
    And verifiedDates 개수는 3이다
    And streakCount는 3이다
    And completedChallenges는 2이다

  Scenario: 인증 기록이 없으면 빈 배열 + streak 0 + completedChallenges 0
    When "user_001"이 크루 "운동 크루"의 내 인증 현황을 조회한다
    Then 응답 코드는 200이다
    And verifiedDates 개수는 0이다
    And streakCount는 0이다
    And completedChallenges는 0이다

  Scenario: 연속 3일 인증 시 streakCount 3
    Given "user_001"이 크루 "운동 크루"에 3일 연속 인증을 완료했다
    When "user_001"이 크루 "운동 크루"의 내 인증 현황을 조회한다
    Then streakCount는 3이다

  Scenario: 비연속 인증 시 최근 연속 구간만 streakCount
    Given "user_001"이 크루 "운동 크루"에 비연속 인증을 완료했다
    When "user_001"이 크루 "운동 크루"의 내 인증 현황을 조회한다
    Then streakCount는 2이다

  # ===== 실패 케이스 =====

  Scenario: 크루 미참여 시 403
    Given 사용자 "user_999"가 로그인되어 있다
    When "user_999"이 크루 "운동 크루"의 내 인증 현황을 조회한다
    Then 응답 코드는 403이다
    And 에러 코드는 "CREW_ACCESS_DENIED"이다

  Scenario: 30일 초과 크루 생성 시 400 거절
    Given 사용자 "user_001"이 로그인되어 있다
    When 기간이 31일인 크루를 생성한다
    Then 응답 코드는 400이다
    And 에러 코드는 "CREW_DURATION_TOO_LONG"이다
