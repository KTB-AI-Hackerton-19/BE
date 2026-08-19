package com.hackathon.backend.dto.asset;

public record PresignedUploadResult(
        String imageKey,
        String uploadUrl,
        int expiresInSeconds
) {
}
