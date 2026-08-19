package com.hackathon.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 프로필 부분 수정. 보내지 않은 필드는 기존 값을 유지한다.
 *
 * <p>로그인 아이디(username)와 비밀번호는 여기서 바꾸지 않는다.</p>
 */
@Schema(description = "프로필 수정 요청. 보내지 않은 필드는 그대로 유지된다.")
public record UserUpdateRequest(
        @Schema(description = "바꿀 이름. 생략하면 그대로", example = "박주승")
        @Size(max = 20, message = "이름은 20자 이하여야 합니다.") String name,

        @Schema(description = "프로필 이미지의 S3 key. presigned URL로 업로드한 뒤 받은 imageKey를 그대로 전달한다",
                example = "profile-images/demo/9f3a....jpg") String profileImageKey,

        @Schema(description = "true면 프로필 이미지를 제거하고 기본 아바타로 되돌린다", example = "false")
        Boolean removeProfileImage
) {
}
