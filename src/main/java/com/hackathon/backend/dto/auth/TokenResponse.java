package com.hackathon.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(description = "API 요청 시 Authorization: Bearer 헤더에 담을 액세스 토큰 (12시간 유효)") String accessToken,
        @Schema(description = "액세스 토큰 재발급용 리프레시 토큰 (7일 유효)") String refreshToken,
        @Schema(description = "화면에 표시할 사용자 이름", example = "박주승") String name
) {
}
