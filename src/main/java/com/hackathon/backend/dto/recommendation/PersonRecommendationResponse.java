package com.hackathon.backend.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 답례 알림이 가장 가까운 날짜에 있는 사람 한 명과, 그 사람을 위한 추천 선물 목록. */
@Schema(description = "답례일이 가까운 사람 한 명 + 추천 선물 목록")
public record PersonRecommendationResponse(
        @Schema(description = "대상 Person ID (특정 대상이 없으면 null)", example = "3") Long personId,
        @Schema(description = "대상 이름", example = "김민수") String person,
        @Schema(description = "챙기는 이유 — REMINDER(답례일) / BIRTHDAY(생일). 대상이 없으면 null", example = "BIRTHDAY")
        String type,
        @Schema(description = "챙길 날짜 — 답례일 또는 생일 (특정 대상이 없으면 null)", example = "2026-09-01") LocalDate reminderDate,
        @Schema(description = "그 날짜까지 남은 일수 (특정 대상이 없으면 null)", example = "3") Integer daysLeft,
        @Schema(description = "이 사람을 위한 추천 선물 목록") List<RecommendationResponse> gifts
) {
}
