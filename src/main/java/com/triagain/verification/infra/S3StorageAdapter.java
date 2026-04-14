package com.triagain.verification.infra;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.triagain.common.port.out.StoragePort;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@Profile("prod")
public class S3StorageAdapter implements StoragePort {

	private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofMinutes(15);
	private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
			"image/jpeg", ".jpg",
			"image/png", ".png",
			"image/webp", ".webp"
	);

	private final S3Presigner s3Presigner;
	private final String bucket;
	private final String region;

	public S3StorageAdapter(S3Presigner s3Presigner,
							@Value("${aws.s3.bucket}") String bucket,
							@Value("${aws.s3.region}") String region) {
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
		this.region = region;
	}

	/** presigned URL 발급 — S3 업로드용 서명된 URL 생성 */
	@Override
	public String generatePresignedUrl(String imageKey, String contentType, long fileSize) {
		PutObjectRequest objectRequest = PutObjectRequest.builder()
				.bucket(bucket)
				.key(imageKey)
				.contentType(contentType)
				.contentLength(fileSize)
				.build();

		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(PRESIGNED_URL_EXPIRY)
				.putObjectRequest(objectRequest)
				.build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
		return presigned.url().toString();
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

	/** 이미지 URL 조회 — S3 키로부터 전체 URL 반환 */
	@Override
	public String getImageUrl(String imageKey) {
		return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + imageKey;
	}

	/** S3 버킷 도메인 반환 — URL 검증 시 사용 */
	@Override
	public String getBucketDomain() {
		return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
	}

	private String resolveExtension(String fileType) {
		String extension = CONTENT_TYPE_EXTENSIONS.get(fileType);
		if (extension == null) {
			throw new IllegalArgumentException("지원하지 않는 파일 타입: " + fileType);
		}
		return extension;
	}
}
