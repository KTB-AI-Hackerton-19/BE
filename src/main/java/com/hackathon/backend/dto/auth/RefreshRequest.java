package com.hackathon.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "로그인 시 발급받은 refresh token")
        @NotBlank(message = "refreshToken을 입력해주세요.") String refreshToken
) {
}
