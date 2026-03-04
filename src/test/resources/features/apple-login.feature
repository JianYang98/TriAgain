# language: ko
@ignore
기능: Apple 로그인 및 회원가입

  배경:
    조건 Apple 토큰 검증 스텁이 설정되어 있다

  시나리오: 신규 유저 Apple 로그인 — isNewUser 응답
    만일 Apple Identity Token으로 로그인을 요청한다
    그러면 응답 코드는 200이다
    그리고 응답의 isNewUser는 true이다
    그리고 응답에 appleId가 존재한다

  시나리오: 신규 유저 Apple 회원가입 — 유저 생성 + JWT 발급
    만일 Apple Identity Token으로 로그인을 요청한다
    그리고 Apple 회원가입을 요청한다
    그러면 응답 코드는 201이다
    그리고 응답에 accessToken이 존재한다
    그리고 응답에 refreshToken이 존재한다
    그리고 응답에 user가 존재한다

  시나리오: 기존 유저 Apple 재로그인 — JWT 발급
    만일 Apple Identity Token으로 로그인을 요청한다
    그리고 Apple 회원가입을 요청한다
    만일 Apple Identity Token으로 로그인을 요청한다
    그러면 응답 코드는 200이다
    그리고 응답의 isNewUser는 false이다
    그리고 응답에 accessToken이 존재한다

  시나리오: Apple 회원가입 시 약관 미동의 — 400 에러
    만일 Apple Identity Token으로 로그인을 요청한다
    그리고 약관 미동의로 Apple 회원가입을 요청한다
    그러면 응답 코드는 400이다
    그리고 에러 코드는 "TERMS_NOT_AGREED"이다

  시나리오: Apple 회원가입 시 닉네임 검증 실패 — 400 에러
    만일 Apple Identity Token으로 로그인을 요청한다
    그리고 잘못된 닉네임으로 Apple 회원가입을 요청한다
    그러면 응답 코드는 400이다
    그리고 에러 코드는 "INVALID_NICKNAME"이다
