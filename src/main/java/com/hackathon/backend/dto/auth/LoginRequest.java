package com.hackathon.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "user01")
        @NotBlank(message = "아이디를 입력해주세요.") String username,

        @Schema(description = "비밀번호", example = "1234")
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
