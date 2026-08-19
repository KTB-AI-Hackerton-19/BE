package com.hackathon.backend.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** 회원탈퇴 결과. 무엇이 얼마나 지워졌는지 돌려줘서 사용자에게 그대로 안내할 수 있게 한다. */
@Schema(description = "회원탈퇴 결과")
public record WithdrawResponse(
        @Schema(description = "삭제된 마음 기록 수", example = "12") int deletedRecords,
        @Schema(description = "삭제된 사람 수", example = "7") int deletedPeople,
        @Schema(description = "삭제된 답례 알림 수", example = "5") int deletedReminders,
        @Schema(description = "삭제된 카테고리 수", example = "7") int deletedCategories,
        @Schema(description = "S3에서 삭제된 이미지 수", example = "3") int deletedImages
) {
}
