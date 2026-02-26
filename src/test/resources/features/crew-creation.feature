Feature: 크루 생성
  크루장이 새로운 크루를 생성하고 초대코드를 발급받는다.

  # ===== Happy Path =====

  Scenario: 텍스트 인증 크루를 생성한다
    Given 사용자 "user_001"이 로그인되어 있다
    When 다음 정보로 크루를 생성한다
      | 이름     | 매일 달리기            |
      | 목표     | 매일 아침 30분 운동하기  |
      | 최대인원  | 10                    |
      | 시작일   | 내일                   |
      | 종료일   | 14일 후                |
      | 인증방식  | TEXT                  |
      | 중간가입  | 허용                   |
    Then 응답 코드는 201이다
    And 응답에 crewId가 존재한다
    And 응답에 inviteCode가 존재한다
    And 크루 상태는 "RECRUITING"이다
    And 현재 인원은 1이다

  Scenario: 사진 인증 크루를 생성한다
    Given 사용자 "user_001"이 로그인되어 있다
    When 인증방식 "PHOTO"로 크루를 생성한다
    Then 응답 코드는 201이다
    And 인증방식은 "PHOTO"이다

  Scenario: 크루 생성자가 자동으로 LEADER로 등록된다
    Given 사용자 "user_001"이 로그인되어 있다
    When 크루를 생성한다
    Then 크루 상세를 조회했을 때 "user_001"의 역할은 "LEADER"이다

  Scenario: 초대코드가 6자리이며 혼동 문자가 제외된다
    Given 사용자 "user_001"이 로그인되어 있다
    When 크루를 생성한다
    Then 초대코드는 6자리이다
    And 초대코드에 "0", "O", "I", "L" 문자가 포함되지 않는다

  # ===== 실패 케이스 =====

  Scenario: 시작일이 오늘이면 크루 생성 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When 시작일을 오늘로 설정하여 크루를 생성한다
    Then 응답 코드는 400이다

  Scenario: 종료일이 시작일보다 빠르면 생성 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When 종료일을 시작일보다 이전으로 설정한다
    Then 응답 코드는 400이다

  Scenario: 크루 이름 없이 생성 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When 크루 이름 없이 크루를 생성한다
    Then 응답 코드는 400이다

  Scenario Outline: 유효하지 않은 인원으로 크루 생성 실패
    Given 사용자 "user_001"이 로그인되어 있다
    When 최대인원 <max>으로 크루 생성을 요청한다
    Then 응답 코드는 400이다

    Examples:
      | max |
      | 0   |
      | 11  |
      | 100 |
