package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GiftRecordExtractRequest(
        @Schema(description = "presigned URL로 S3에 업로드 완료된 이미지의 key. 한 번에 한 장만 보낸다 "
                + "(그 한 장에 여러 명이 있으면 응답의 records에 사람 수만큼 담겨 온다)")
        @NotBlank(message = "imageKey를 입력해주세요.") String imageKey
) {
}
