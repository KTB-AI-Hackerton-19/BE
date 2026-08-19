package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.user.UserResponse;
import com.hackathon.backend.dto.user.UserUpdateRequest;
import com.hackathon.backend.dto.user.WithdrawResponse;
import com.hackathon.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 프로필", description = "프로필 조회·수정과 회원탈퇴")
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "내 프로필 조회",
            description = "로그인 아이디, 표시 이름, 프로필 이미지 URL을 돌려준다. "
                    + "프로필 이미지를 설정하지 않았으면 profileImageUrl이 null이므로 이름 첫 글자 아바타로 대체하면 된다."
    )
    @GetMapping
    public ApiResponse<UserResponse> me() {
        return ApiResponse.success(userService.me());
    }

    @Operation(
            summary = "프로필 수정",
            description = "이름과 프로필 이미지를 바꾼다. 보내지 않은 필드는 기존 값이 유지된다. "
                    + "이미지는 선물 사진과 같은 방식으로, POST /api/gift-assets/presigned-url 에 purpose=PROFILE로 "
                    + "URL을 발급받아 S3에 직접 올린 뒤 받은 imageKey를 profileImageKey로 전달한다. "
                    + "이미지를 지우고 기본 아바타로 되돌리려면 removeProfileImage=true."
    )
    @PatchMapping
    public ApiResponse<UserResponse> update(@Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.update(request));
    }

    @Operation(
            summary = "회원탈퇴",
            description = "계정과 그 사용자의 모든 데이터를 삭제한다 — 마음 기록, 사람, 답례 알림, 선물 추천, "
                    + "카테고리, S3에 올린 이미지까지 전부. **되돌릴 수 없다.** "
                    + "응답으로 무엇이 몇 건 지워졌는지 돌려주므로 확인 화면에 그대로 쓰면 된다."
    )
    @DeleteMapping
    public ApiResponse<WithdrawResponse> withdraw() {
        return ApiResponse.success(userService.withdraw());
    }
}
