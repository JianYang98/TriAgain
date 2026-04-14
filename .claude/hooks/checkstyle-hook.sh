#!/bin/bash
# PostToolUse Hook: .java 파일 수정 시 Checkstyle 단일 파일 검사

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# .java가 아니거나 파일이 없으면 스킵
if [[ -z "$FILE_PATH" || "$FILE_PATH" != *.java ]]; then
  exit 0
fi
if [[ ! -f "$FILE_PATH" ]]; then
  exit 0
fi

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$PROJECT_ROOT/config/checkstyle/checkstyle-10.21.1-all.jar"
CONFIG="$PROJECT_ROOT/config/checkstyle/triagain-checkstyle-rules.xml"
SUPPRESSIONS="$PROJECT_ROOT/config/checkstyle/triagain-checkstyle-suppressions.xml"

# jar 없으면 스킵 (블로킹하지 않음)
if [[ ! -f "$JAR" ]]; then
  exit 0
fi

OUTPUT=$(java -jar "$JAR" \
  -c "$CONFIG" \
  -p <(echo "suppressionFile=$SUPPRESSIONS") \
  "$FILE_PATH" 2>&1)
EXIT_CODE=$?

# Checkstyle 실행 자체가 실패한 경우 (파싱 에러, 설정 오류 등)
if [[ $EXIT_CODE -ne 0 ]]; then
  ERRORS=$(echo "$OUTPUT" | grep "\[ERROR\]")
  if [[ -n "$ERRORS" ]]; then
    echo "Checkstyle 실행 에러:" >&2
    echo "$ERRORS" >&2
  else
    echo "Checkstyle 실행 실패 (exit code: $EXIT_CODE):" >&2
    echo "$OUTPUT" >&2
  fi
  exit 2
fi

# severity=warning이면 exit 0이므로, [WARN] 출력 여부로 판단
VIOLATIONS=$(echo "$OUTPUT" | grep "\[WARN\]")
if [[ -n "$VIOLATIONS" ]]; then
  echo "Checkstyle 위반 발견:" >&2
  echo "$VIOLATIONS" >&2
  echo "" >&2
  echo "위반 사항을 수정해주세요." >&2
  exit 2
fi

exit 0
