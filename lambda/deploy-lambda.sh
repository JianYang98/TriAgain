#!/bin/bash
# TriAgain Lambda 배포 스크립트
# 사용법: ./deploy-lambda.sh <backend-url> <internal-api-key> [s3-bucket-name]
#
# 예시:
#   ./deploy-lambda.sh https://api.triagain.com my-secret-key
#   ./deploy-lambda.sh https://api.triagain.com my-secret-key triagain-verifications

set -euo pipefail

BACKEND_URL="${1:?Usage: ./deploy-lambda.sh <backend-url> <internal-api-key> [s3-bucket-name]}"
INTERNAL_API_KEY="${2:?Usage: ./deploy-lambda.sh <backend-url> <internal-api-key> [s3-bucket-name]}"
S3_BUCKET_NAME="${3:-triagain-verifications}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STACK_NAME="triagain-lambda"
REGION="ap-northeast-2"
FUNCTION_NAME="triagain-upload-complete"

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

echo "=== S3 Notification 설정 ==="
LAMBDA_ARN=$(aws lambda get-function \
  --function-name "$FUNCTION_NAME" \
  --region "$REGION" \
  --query 'Configuration.FunctionArn' \
  --output text)

# Lambda에 S3 invoke 권한 부여 (이미 존재하면 무시)
aws lambda add-permission \
  --function-name "$FUNCTION_NAME" \
  --region "$REGION" \
  --statement-id "s3-invoke-${S3_BUCKET_NAME}" \
  --action lambda:InvokeFunction \
  --principal s3.amazonaws.com \
  --source-arn "arn:aws:s3:::${S3_BUCKET_NAME}" \
  2>/dev/null || echo "S3 invoke 권한이 이미 존재합니다."

# S3 버킷에 Lambda notification 설정
aws s3api put-bucket-notification-configuration \
  --bucket "$S3_BUCKET_NAME" \
  --region "$REGION" \
  --notification-configuration "{
    \"LambdaFunctionConfigurations\": [
      {
        \"LambdaFunctionArn\": \"${LAMBDA_ARN}\",
        \"Events\": [\"s3:ObjectCreated:Put\"],
        \"Filter\": {
          \"Key\": {
            \"FilterRules\": [
              {\"Name\": \"prefix\", \"Value\": \"upload-sessions/\"}
            ]
          }
        }
      }
    ]
  }"

echo "=== 배포 완료 ==="
echo "Stack: $STACK_NAME"
echo "Region: $REGION"
echo "Lambda: $LAMBDA_ARN"
echo "S3 Notification: $S3_BUCKET_NAME → $FUNCTION_NAME"
