package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.asset.PresignedUploadResult;
import com.hackathon.backend.dto.asset.PresignedUrlRequest;
import com.hackathon.backend.dto.asset.PresignedUrlResponse;
import com.hackathon.backend.security.SecurityUtils;
import com.hackathon.backend.service.S3PresignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이미지 업로드", description = "선물 사진을 S3에 직접 업로드하기 위한 presigned URL 발급")
@RestController
@RequestMapping("/api/gift-assets")
public class GiftAssetController {

    private final S3PresignService s3PresignService;

    public GiftAssetController(S3PresignService s3PresignService) {
        this.s3PresignService = s3PresignService;
    }

    @Operation(
            summary = "presigned URL 발급",
            description = "사진 촬영 직후 호출. 발급받은 uploadUrl로 클라이언트가 S3에 직접 PUT 업로드하고, "
                    + "이후 imageKey를 /api/gift-records/extract에 전달해 AI 분석을 요청한다. "
                    + "프로필 사진을 올릴 때는 purpose=PROFILE로 요청하고, 받은 imageKey를 "
                    + "PATCH /api/users/me 의 profileImageKey로 전달한다."
    )
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> presignedUrl(@Valid @RequestBody PresignedUrlRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        // purpose에 따라 저장 경로를 나눈다(gift-images / profile-images). 생략하면 선물 사진.
        String prefix = "PROFILE".equalsIgnoreCase(request.purpose())
                ? S3PresignService.PROFILE_PREFIX
                : S3PresignService.GIFT_PREFIX;
        PresignedUploadResult result =
                s3PresignService.createPutUrl(username, request.fileName(), request.contentType(), prefix);
        return ApiResponse.success(new PresignedUrlResponse(result.imageKey(), result.uploadUrl(), result.expiresInSeconds()));
    }
}
