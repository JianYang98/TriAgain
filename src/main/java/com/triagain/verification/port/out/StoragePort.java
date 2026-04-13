package com.triagain.verification.port.out;

public interface StoragePort {

    String generatePresignedUrl(String imageKey, String contentType);

    String generateImageKey(String userId, String fileName);

    /** prefix 지정 이미지 키 생성 — 프로필 이미지 등 upload-sessions 외 경로에서 사용 */
    String generateImageKey(String prefix, String userId, String fileName);

    String getImageUrl(String imageKey);

    /** S3 버킷 도메인 반환 — URL 검증 시 사용 */
    String getBucketDomain();
}
