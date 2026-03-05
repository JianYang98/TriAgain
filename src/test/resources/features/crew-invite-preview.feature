Feature: 초대코드로 크루 미리보기
  사용자가 초대코드로 크루 정보를 미리 확인하고 가입 가능 여부를 판단한다.

  Background:
    Given 사용자 "leader_001"이 크루를 생성했다

  # ===== Happy Path =====

  Scenario: 유효한 초대코드로 크루 미리보기 조회
    Given 사용자 "user_002"가 로그인되어 있다
    When 초대코드로 크루 미리보기를 요청한다
    Then 응답 코드는 200이다
    And 미리보기 응답에 크루 이름이 존재한다
    And 가입 가능 여부는 true이다
    And 가입 차단 사유는 없다

  Scenario: 이미 가입한 크루의 초대코드로 조회하면 ALREADY_MEMBER
    Given 사용자 "user_002"가 크루에 참여했다
    When "user_002"가 초대코드로 크루 미리보기를 요청한다
    Then 응답 코드는 200이다
    And 가입 가능 여부는 false이다
    And 가입 차단 사유는 "ALREADY_MEMBER"이다

  Scenario: 정원 초과 크루의 초대코드로 조회하면 CREW_FULL
    Given 크루 정원이 가득 찼다
    And 사용자 "user_999"가 로그인되어 있다
    When 초대코드로 크루 미리보기를 요청한다
    Then 응답 코드는 200이다
    And 가입 가능 여부는 false이다
    And 가입 차단 사유는 "CREW_FULL"이다

  # ===== 실패 케이스 =====

  Scenario: 잘못된 초대코드로 미리보기 요청 시 404
    Given 사용자 "user_002"가 로그인되어 있다
    When 초대코드 "XXXXXX"로 크루 미리보기를 요청한다
    Then 응답 코드는 404이다
    And 에러 코드는 "INVALID_INVITE_CODE"이다
