package com.triagain.verification.infra;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.triagain.common.port.out.StoragePort;

@Component
@Profile("!prod")
public class LocalStorageAdapter implements StoragePort {

	private static final String LOCAL_BASE_URL = "http://localhost:8080/local-storage";
	private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
			"image/jpeg", ".jpg",
			"image/png", ".png",
			"image/webp", ".webp"
	);

	/** presigned URL 발급 — 로컬 환경용 모의 URL 생성 */
	@Override
	public String generatePresignedUrl(String imageKey, String contentType, long fileSize) {
		return LOCAL_BASE_URL + "/" + imageKey + "?presigned=true";
	}

	/** 이미지 키 생성 — upload-sessions 기본 경로 */
	@Override
	public String generateImageKey(String userId, String fileType) {
		String extension = resolveExtension(fileType);
		return "upload-sessions/" + userId + "/" + UUID.randomUUID() + extension;
	}

	/** prefix 지정 이미지 키 생성 — 프로필 이미지 등 upload-sessions 외 경로에서 사용 */
	@Override
	public String generateImageKey(String prefix, String userId, String fileType) {
		String extension = resolveExtension(fileType);
		return prefix + "/" + userId + "/" + UUID.randomUUID() + extension;
	}

	/** 이미지 URL 조회 — 로컬 환경용 URL 반환 */
	@Override
	public String getImageUrl(String imageKey) {
		return LOCAL_BASE_URL + "/" + imageKey;
	}

	/** S3 버킷 도메인 반환 — URL 검증 시 사용 */
	@Override
	public String getBucketDomain() {
		return LOCAL_BASE_URL + "/";
	}

	private String resolveExtension(String fileType) {
		String extension = CONTENT_TYPE_EXTENSIONS.get(fileType);
		if (extension == null) {
			throw new IllegalArgumentException("지원하지 않는 파일 타입: " + fileType);
		}
		return extension;
	}
}
