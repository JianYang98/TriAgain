#!/usr/bin/env bash
set -euo pipefail
echo "빌드 중..."
./gradlew bootJar
echo "EC2로 전송 중..."
scp -i triagain-key.pem build/libs/triagain-0.0.1-SNAPSHOT.jar ec2-user@3.34.132.7:~/
echo "배포 완료! EC2에서 ~/dev_start.sh 또는 ~/prod_start.sh 실행하세요"
    