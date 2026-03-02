package com.triagain.verification.infra;

import com.triagain.verification.port.out.StoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Component
@Profile("prod")
public class S3StorageAdapter implements StoragePort {

    private static final Duration PRESIGNED_URL_EXPIRY = Duration.ofMinutes(15);

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

    @Override
    public String generatePresignedUrl(String imageKey, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRY)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public String generateImageKey(String userId, String fileName) {
        String extension = extractExtension(fileName);
        return "upload-sessions/" + userId + "/" + UUID.randomUUID() + extension;
    }

    @Override
    public String getImageUrl(String imageKey) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + imageKey;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
