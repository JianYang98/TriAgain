#!/usr/bin/env bash
# TriAgain 부하테스트용 EC2 접속/전송 헬퍼 (feat/load-test 한정)
# ⚠️ "맥(로컬)"에서 실행. EC2 안에서 돌리면 키가 없어 실패한다.
set -euo pipefail

KEY="${TRIAGAIN_KEY:-$HOME/Downloads/triagain-key.pem}"
HOST="ec2-user@15.164.69.243"
JAR="/Users/jian/Projects/triagain/triagain-back/build/libs/triagain-0.0.1-SNAPSHOT.jar"
REMOTE_JAR="triagain-loadtest.jar"   # prod jar(컨테이너) 안 건드리게 별도 파일명

[ -f "$KEY" ] || { echo "❌ 키 없음: $KEY  (TRIAGAIN_KEY 로 경로 지정 가능)"; exit 1; }
chmod 400 "$KEY" 2>/dev/null || true

case "${1:-help}" in
  push)
    [ -f "$JAR" ] || { echo "❌ jar 없음: $JAR  (./gradlew bootJar 먼저)"; exit 1; }
    echo "▶ 전송: $(basename "$JAR") ($(du -h "$JAR" | cut -f1)) → $HOST:~/$REMOTE_JAR"
    scp -i "$KEY" "$JAR" "$HOST:~/$REMOTE_JAR"
    echo "✅ 전송 완료. 다음: $0 ssh 로 들어가 'java -version' 확인 후 기동"
    ;;
  ssh)
    ssh -i "$KEY" "$HOST"
    ;;
  run)
    [ -n "${2:-}" ] || { echo "사용법: $0 run '<원격명령>'"; exit 1; }
    ssh -i "$KEY" "$HOST" "$2"
    ;;
  *)
    echo "사용법: $0 {push|ssh|run '<원격명령>'}"
    echo "  push : 테스트 jar → EC2 ~/$REMOTE_JAR 전송"
    echo "  ssh  : EC2 접속"
    echo "  run  : EC2에서 원격 명령 1회 실행 (예: $0 run 'docker ps')"
    ;;
esac
