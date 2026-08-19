package com.hackathon.backend.dto.category;

import com.hackathon.backend.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리. 마음 기록 화면의 필터 칩과 기록 모달의 카테고리 select를 이 목록으로 그린다.")
public record CategoryResponse(
        @Schema(description = "카테고리 ID (기록 등록/수정 시 categoryId로 전달)", example = "1") Long id,
        @Schema(description = "카테고리 이름 (화면에 그대로 노출)", example = "디저트") String name,
        @Schema(description = "기본 이모지 — 기록 카드 좌측 원형에 표시", example = "🍰") String emoji,
        @Schema(description = "카드 배경 테마 (mint/pink/blue/gold) — 프론트 CSS 클래스명과 1:1", example = "mint") String color,
        @Schema(description = "정렬 순서 (작을수록 앞)", example = "10") Integer displayOrder,
        @Schema(description = "노출 여부", example = "true") boolean active,
        @Schema(description = "현재 로그인 사용자가 이 카테고리로 기록한 건수. 0인 칩을 숨기고 싶을 때 사용", example = "3") long recordCount
) {
    public static CategoryResponse from(Category category, long recordCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getEmoji(),
                category.getColor(),
                category.getDisplayOrder(),
                category.isActive(),
                recordCount
        );
    }
}
