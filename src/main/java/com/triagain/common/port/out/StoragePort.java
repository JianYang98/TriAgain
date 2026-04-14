package com.triagain.common.port.out;

public interface StoragePort {

	String PROFILE_PREFIX = "profiles";

	/** presigned URL 발급 — S3 업로드용 서명된 URL 생성 */
	String generatePresignedUrl(String imageKey, String contentType, long fileSize);

	/** 이미지 키 생성 — upload-sessions 기본 경로 */
	String generateImageKey(String userId, String fileType);

	/** prefix 지정 이미지 키 생성 — 프로필 이미지 등 upload-sessions 외 경로에서 사용 */
	String generateImageKey(String prefix, String userId, String fileType);

	/** 이미지 URL 조회 — S3 키로부터 전체 URL 반환 */
	String getImageUrl(String imageKey);

	/** S3 버킷 도메인 반환 — URL 검증 시 사용 */
	String getBucketDomain();
}
