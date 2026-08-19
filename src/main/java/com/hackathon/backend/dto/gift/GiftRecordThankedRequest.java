package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "감사/답례 완료 여부 토글 요청")
public record GiftRecordThankedRequest(
        @Schema(description = "true면 '감사 완료', false면 '확인 필요' 뱃지로 바뀐다", example = "true")
        @NotNull(message = "thanked 값을 입력해주세요.") Boolean thanked
) {
}
