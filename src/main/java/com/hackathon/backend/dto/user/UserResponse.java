package com.hackathon.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필")
public record UserResponse(
        @Schema(description = "로그인 아이디 (변경 불가)", example = "demo") String username,
        @Schema(description = "화면에 표시되는 이름", example = "데모유저") String name,
        @Schema(description = "프로필 이미지 조회용 presigned URL. 없으면 null (프론트는 이름 첫 글자 아바타로 대체)")
        String profileImageUrl
) {
}
