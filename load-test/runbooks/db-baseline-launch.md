# 실험 앱 기동·준비

테스트 전용 서버·DB와 비공개 환경값을 확인한 후 수동 실행한다. 운영 서버·DB·이미지 태그를 사용하지 않는다.

## 이미지와 설정

- 과거 실험은 동결 소스의 고정 이미지를 DB/RD에서 함께 사용했다. 이 사본으로 새로 빌드한 이미지의 동일성은 별도 검증 대상이다.
- Java 17에서 `./gradlew bootJar --no-daemon`을 실행하고, 실행 JAR 한 개(`-plain.jar` 제외)의 SHA256을 기록한다. [Dockerfile.runtime](../docker/Dockerfile.runtime)을 사용한 조립·배포는 이번 정리에서 실행하지 않았다.
- 비공개 env에 테스트용 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_API_KEY`, `APPLE_REFRESH_KEY`를 주입한다. 암호화키는 앱이 요구하는 AES 형식의 테스트값을 사용한다. 비밀값은 CLI 인자·로그·Git에 남기지 않는다.
- 지표 노출·application 태그·히스토그램 설정은 loadtest 프로필에 있다. 공통 운영 설정은 이식하지 않았다.

```bash
docker run -d --name "$APP_CONTAINER" --restart=no -p 8080:8080 \
  --env-file "$ENV_FILE" "$IMAGE_TAG" \
  --spring.profiles.active=prod,loadtest \
  --spring.flyway.enabled=false \
  --triagain.crew.lock-strategy=CONDITIONAL
```

위는 대상 확인과 기존 로그 보존을 마친 뒤 사용하는 형식 예시다. 같은 이름의 컨테이너가 있으면 중단한다. REDIS_ASYNC는 전략 인자를 바꾸고 Redis host/port, namespace/run-id를 추가한다. 새 RUN_ID를 사용할 때 새 JVM도 같은 RUN_ID를 바라봐야 한다.

## 기동 후

1. Health UP과 고정 이미지 ID·실행 인자를 확인한다.
2. RD는 startup PING·recovery 성공과 Worker 오류를 확인한다. Health만으로 Worker를 판정하지 않는다.
3. 테스트 DB 이름·current_user, 새 크루 초기 인원 1과 Redis members/seq 1, 큐 0/0을 확인한다.
4. [재현 가이드](../DBB-REPRODUCTION-GUIDE.md)의 워밍업·계측·READY 순서를 따른다.

`prod,loadtest`는 격리 실험 환경용 조합이다. 실제 인프라값과 원본 실행 기록은 개인 보관 자료이며 이 저장소에 포함하지 않는다.
