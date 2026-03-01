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

    @Override
    public String getImageUrl(String imageKey) {
        return LOCAL_BASE_URL + "/" + imageKey;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }
}
