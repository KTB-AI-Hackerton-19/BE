package com.hackathon.backend.client;

import java.util.List;

/**
 * 선물 추천 응답 스키마 (AI 팀과 합의해 고정).
 *
 * <pre>
 * {
 *   "items": [
 *     { "emoji": "☕", "name": "스페셜티 드립백 세트", "amount": 32000,
 *       "tag": "취향 일치", "reason": "민수님이 커피를 좋아하고, 받은 선물과 부담이 비슷해요." }
 *   ]
 * }
 * </pre>
 *
 * <p>{@code tag}는 "취향 일치" / "실패 확률 낮음" / "답례 추천" 세 가지 중 하나 (모르는 값이면 "실패 확률 낮음"으로 처리).</p>
 */
public record AiRecommendResponse(
        List<Item> items
) {
    public record Item(
            String emoji,
            String name,
            Integer amount,
            String tag,
            String reason
    ) {
    }
}
