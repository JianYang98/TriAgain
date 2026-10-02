package com.triagain.verification.api;

import com.triagain.verification.domain.vo.UploadSessionStatus;

public record UploadSessionStatusResponse(Long uploadSessionId, UploadSessionStatus status) {
}
