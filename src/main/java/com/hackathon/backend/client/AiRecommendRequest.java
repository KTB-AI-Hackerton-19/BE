package com.hackathon.backend.client;

import java.time.LocalDate;
import java.util.List;

/**
 * 선물 추천 요청 바디 ({@code POST {AI_SERVICE_URL}/recommendations}).
 *
 * <pre>
 * {
 *   "personName": "김민수",
 *   "relationship": "친한 친구",
 *   "memo": "커피를 좋아함",
 *   "limit": 3,
 *   "recentGifts": [
 *     { "giftName": "스타벅스 케이크", "category": "디저트", "amount": 35000,
 *       "occasion": "내 생일", "receivedDate": "2026-08-18" }
 *   ]
 * }
 * </pre>
 */
public record AiRecommendRequest(
        String personName,
        String relationship,
        String memo,
        int limit,
        List<ReceivedGift> recentGifts
) {
    public record ReceivedGift(
            String giftName,
            String category,
            Integer amount,
            String occasion,
            LocalDate receivedDate
    ) {
    }
}
