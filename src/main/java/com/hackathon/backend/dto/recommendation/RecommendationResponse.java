package com.hackathon.backend.dto.recommendation;

import com.hackathon.backend.domain.RecommendedGift;
import com.hackathon.backend.support.MoneyFormatter;
import io.swagger.v3.oas.annotations.media.Schema;

/** 홈 화면 "이런 선물은 어때요?" 추천 카드 한 장. */
@Schema(description = "선물 추천 카드")
public record RecommendationResponse(
        @Schema(description = "추천 ID", example = "1") Long id,
        @Schema(description = "추천 대상 Person ID (대상이 없으면 null)", example = "3") Long personId,
        @Schema(description = "추천 대상 이름 — '민수님을 위한 추천' 문구에 사용", example = "김민수") String person,
        @Schema(description = "상품 이모지", example = "☕") String emoji,
        @Schema(description = "상품명", example = "스페셜티 드립백 세트") String name,
        @Schema(description = "예상 금액(원)", example = "32000") Integer amount,
        @Schema(description = "포맷된 금액 문자열 — 화면의 price", example = "32,000원") String price,
        @Schema(description = "뱃지 문구 (취향 일치 / 실패 확률 낮음 / 답례 추천)", example = "취향 일치") String tag,
        @Schema(description = "추천 이유 한 문장", example = "민수님이 커피를 좋아하고, 받은 선물과 부담이 비슷해요.") String reason
) {
    public static RecommendationResponse from(RecommendedGift gift) {
        return new RecommendationResponse(
                gift.getId(),
                gift.getPerson() != null ? gift.getPerson().getId() : null,
                gift.getPerson() != null ? gift.getPerson().getName() : null,
                gift.getEmoji(),
                gift.getName(),
                gift.getAmount(),
                MoneyFormatter.format(gift.getAmount()),
                gift.getTag().getLabel(),
                gift.getReason()
        );
    }
}
