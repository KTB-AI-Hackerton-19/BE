package com.hackathon.backend.dto.category;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftKind;
import com.hackathon.backend.support.MoneyFormatter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "카테고리. 마음 기록 화면의 필터 칩과 기록 모달의 카테고리 select를 이 목록으로 그린다.")
public record CategoryResponse(
        @Schema(description = "속한 탭 — GIFT(선물) / CELEBRATION(경사) / CONDOLENCE(조사)") GiftKind kind,
        @Schema(description = "탭 한글 라벨", example = "경사") String kindLabel,
        @Schema(description = "경조사 탭 소속 여부(경사 또는 조사)", example = "true") boolean event,
        @Schema(description = "카테고리 ID (기록 등록/수정 시 categoryId로 전달)", example = "1") Long id,
        @Schema(description = "카테고리 이름 (화면에 그대로 노출)", example = "디저트") String name,
        @Schema(description = "기본 이모지 — 기록 카드 좌측 원형에 표시", example = "🍰") String emoji,
        @Schema(description = "카드 배경 테마 (mint/pink/blue/gold) — 프론트 CSS 클래스명과 1:1", example = "mint") String color,
        @Schema(description = "정렬 순서 (작을수록 앞)", example = "10") Integer displayOrder,
        @Schema(description = "노출 여부", example = "true") boolean active,
        @Schema(description = "현재 로그인 사용자가 이 카테고리로 기록한 건수. 경조사 탭에서는 '몇 명에게 받았는지'가 된다", example = "32") long recordCount,
        @Schema(description = "이 카테고리 기록의 금액 합계", example = "1240000") long totalAmount,
        @Schema(description = "포맷된 금액 합계. 그대로 출력하면 된다", example = "1,240,000원") String totalAmountText,
        @Schema(description = "가장 최근에 받은 날짜. 경조사 카드의 날짜 표시와 정렬에 쓴다 (기록이 없으면 null)",
                example = "2026-05-10") LocalDate latestDate
) {
    public static CategoryResponse from(Category category, long recordCount) {
        return from(category, recordCount, 0L, null);
    }

    public static CategoryResponse from(Category category, long recordCount, long totalAmount, LocalDate latestDate) {
        return new CategoryResponse(
                category.getKind(),
                category.getKind() != null ? category.getKind().getLabel() : null,
                category.getKind() != null && category.getKind().isEvent(),
                category.getId(),
                category.getName(),
                category.getEmoji(),
                category.getColor(),
                category.getDisplayOrder(),
                category.isActive(),
                recordCount,
                totalAmount,
                MoneyFormatter.format((int) totalAmount),
                latestDate
        );
    }
}
