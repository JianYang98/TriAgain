package com.triagain.verification.infra;

import com.triagain.verification.port.out.StoragePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!prod")
public class LocalStorageAdapter implements StoragePort {

    private static final String LOCAL_BASE_URL = "http://localhost:8080/local-storage";

    @Override
    public String generatePresignedUrl(String imageKey, String contentType) {
        return LOCAL_BASE_URL + "/" + imageKey + "?presigned=true";
    }

    @Override
    public String generateImageKey(String userId, String fileName) {
        String extension = extractExtension(fileName);
        return "upload-sessions/" + userId + "/" + UUID.randomUUID() + extension;
    }

    /** prefix 지정 이미지 키 생성 — 프로필 이미지 등 upload-sessions 외 경로에서 사용 */
    @Override
    public String generateImageKey(String prefix, String userId, String fileName) {
        String extension = extractExtension(fileName);
        return prefix + "/" + userId + "/" + UUID.randomUUID() + extension;
    }

    @Override
    public String getImageUrl(String imageKey) {
        return LOCAL_BASE_URL + "/" + imageKey;
    }

    /** S3 버킷 도메인 반환 — URL 검증 시 사용 */
    @Override
    public String getBucketDomain() {
        return LOCAL_BASE_URL + "/";
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
