package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GiftRecordExtractRequest(
        @Schema(description = "presigned URL로 S3에 업로드 완료된 이미지의 key")
        @NotBlank(message = "imageKey를 입력해주세요.") String imageKey
) {
}
