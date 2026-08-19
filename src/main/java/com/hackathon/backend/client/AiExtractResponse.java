package com.hackathon.backend.client;

import java.time.LocalDate;

/**
 * AI 서비스가 돌려주는 분석 결과 스키마 (AI 팀과 합의해 고정).
 *
 * <pre>
 * {
 *   "senderName":   "김민수",
 *   "relationship": "친한 친구",
 *   "receivedDate": "2026-08-18",
 *   "occasion":     "내 생일",
 *   "giftName":     "스타벅스 케이크",
 *   "category":     "디저트",
 *   "amount":       35000,
 *   "confidence":   0.82
 * }
 * </pre>
 *
 * <p>{@code category}는 서버의 categories 테이블 이름과 매칭된다. 모르는 이름이면 "기타"로 떨어진다.
 * 값을 못 채운 필드는 임의로 지어내지 말고 null로 보내면 된다 (사용자가 확인 폼에서 직접 채운다).</p>
 */
public record AiExtractResponse(
        String senderName,
        String relationship,
        LocalDate receivedDate,
        String occasion,
        String giftName,
        String category,
        Integer amount,
        Double confidence
) {
}
