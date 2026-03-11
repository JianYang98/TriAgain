#!/bin/bash
# TriAgain Lambda 배포 스크립트
# 사용법: ./deploy.sh <backend-url> <internal-api-key> [s3-bucket-name]
#
# 예시:
#   ./deploy.sh https://api.triagain.com my-secret-key
#   ./deploy.sh https://api.triagain.com my-secret-key triagain-verifications

set -euo pipefail

BACKEND_URL="${1:?Usage: ./deploy.sh <backend-url> <internal-api-key> [s3-bucket-name]}"
INTERNAL_API_KEY="${2:?Usage: ./deploy.sh <backend-url> <internal-api-key> [s3-bucket-name]}"
S3_BUCKET_NAME="${3:-triagain-verifications}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STACK_NAME="triagain-lambda"
REGION="ap-northeast-2"

echo "=== SAM Build ==="
sam build --template-file "$SCRIPT_DIR/template.yaml"

echo "=== SAM Deploy ==="
sam deploy \
  --stack-name "$STACK_NAME" \
  --region "$REGION" \
  --resolve-s3 \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    "BackendUrl=$BACKEND_URL" \
    "InternalApiKey=$INTERNAL_API_KEY" \
    "S3BucketName=$S3_BUCKET_NAME" \
  --no-confirm-changeset

echo "=== 배포 완료 ==="
echo "Stack: $STACK_NAME"
echo "Region: $REGION"
