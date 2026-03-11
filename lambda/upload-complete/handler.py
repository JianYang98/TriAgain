"""
S3 업로드 완료 감지 Lambda — PutObject 이벤트 → Spring Boot 내부 API 호출

트리거: S3 PutObject (prefix: upload-sessions/)
동작: imageKey 추출 → PUT /internal/upload-sessions/complete?imageKey={key}
"""

import json
import os
import urllib.parse
import urllib.request
import urllib.error
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

BACKEND_URL = os.environ["BACKEND_URL"]  # e.g. https://api.triagain.com
INTERNAL_API_KEY = os.environ["INTERNAL_API_KEY"]
TARGET_PREFIX = "upload-sessions/"


def handler(event, context):
    """S3 PutObject 이벤트 핸들러"""
    for record in event.get("Records", []):
        s3_info = record.get("s3", {})
        bucket = s3_info.get("bucket", {}).get("name", "")
        key = urllib.parse.unquote_plus(s3_info.get("object", {}).get("key", ""))

        logger.info("S3 event: bucket=%s, key=%s", bucket, key)

        if not key.startswith(TARGET_PREFIX):
            logger.info("Skipping non-target key: %s", key)
            continue

        _notify_backend(key)

    return {"statusCode": 200, "body": "OK"}


def _notify_backend(image_key):
    """Spring Boot 내부 API에 업로드 완료 알림"""
    encoded_key = urllib.parse.quote(image_key, safe="")
    url = f"{BACKEND_URL}/internal/upload-sessions/complete?imageKey={encoded_key}"

    req = urllib.request.Request(
        url,
        method="PUT",
        headers={
            "X-Internal-Api-Key": INTERNAL_API_KEY,
            "Content-Type": "application/json",
        },
        data=b"",
    )

    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            logger.info("Backend responded: %d for key=%s", resp.status, image_key)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        logger.error("Backend error: %d %s for key=%s", e.code, body, image_key)
        raise
    except urllib.error.URLError as e:
        logger.error("Connection error: %s for key=%s", e.reason, image_key)
        raise
