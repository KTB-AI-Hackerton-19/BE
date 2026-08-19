package com.hackathon.backend.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;

public record PresignedUrlResponse(
        @Schema(description = "S3에 저장될 이미지 key. 업로드 완료 후 /api/gift-records/extract 호출 시 그대로 전달") String imageKey,
        @Schema(description = "이 URL로 클라이언트가 S3에 직접 PUT 요청하면 업로드된다 (백엔드 경유 안 함)") String uploadUrl,
        @Schema(description = "uploadUrl의 유효시간(초)", example = "300") int expiresInSeconds
) {
}
