package com.hackathon.backend.dto.gift;

import com.hackathon.backend.domain.EventCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/** 경조사 유형 하나. GET /api/gift-records/event-categories 로 고정 7종 전체를 내려준다. */
@Schema(description = "경조사 유형")
public record EventCategoryResponse(
        @Schema(description = "기록 등록/수정 시 eventCategory로 보낼 값", example = "WEDDING") String name,
        @Schema(description = "화면에 그대로 노출할 한글 라벨", example = "결혼") String label,
        @Schema(description = "소속 그룹 — CELEBRATION(경사) / CONDOLENCE(조사)", example = "CELEBRATION") String group,
        @Schema(description = "그룹 한글 라벨", example = "경사") String groupLabel,
        @Schema(description = "기본 이모지", example = "💍") String emoji,
        @Schema(description = "카드 배경 테마", example = "gold") String color
) {
    public static EventCategoryResponse from(EventCategory category) {
        return new EventCategoryResponse(
                category.name(),
                category.getLabel(),
                category.getGroup().name(),
                category.getGroup().getLabel(),
                category.getEmoji(),
                category.getColor());
    }
}
