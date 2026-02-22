package com.triagain.verification.port.out;

public interface StoragePort {

    String generatePresignedUrl(String imageKey, String contentType);

    String generateImageKey(String userId, String fileName);

    String getImageUrl(String imageKey);
}
