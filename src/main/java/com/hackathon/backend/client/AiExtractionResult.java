package com.hackathon.backend.client;

import java.time.LocalDate;

/**
 * AI 분석 결과를 서비스 계층으로 넘기는 내부 값 객체.
 *
 * <p>{@code fallback}이 true면 AI가 아니라 <b>하드코딩 더미</b>다. 이 값이 응답까지 그대로 올라가야
 * "AI가 잘 도는 줄 알았는데 실은 더미였다"를 눈으로 구분할 수 있다.</p>
 */
public record AiExtractionResult(
        String senderName,
        String relationship,
        LocalDate receivedDate,
        String occasion,
        String giftName,
        String categoryName,
        Integer amount,
        boolean fallback,
        /** 폴백이면 왜 폴백했는지. AI가 준 에러 본문이 그대로 들어간다. 정상이면 null. */
        String fallbackReason
) {
}
