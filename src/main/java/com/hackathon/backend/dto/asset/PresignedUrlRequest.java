package com.hackathon.backend.dto.asset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PresignedUrlRequest(
        @Schema(description = "업로드할 파일명 (확장자 포함, jpg/jpeg/png/heic만 허용)", example = "photo.jpg")
        @NotBlank(message = "fileName을 입력해주세요.") String fileName,

        @Schema(description = "파일 Content-Type (image/*만 허용)", example = "image/jpeg")
        @NotBlank(message = "contentType을 입력해주세요.") String contentType
) {
}
