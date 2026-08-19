package com.hackathon.backend.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "로그인 아이디", example = "user01")
        @NotBlank(message = "아이디를 입력해주세요.") String username,

        @Schema(description = "비밀번호 (4자 이상)", example = "1234")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.") String password
) {
}
