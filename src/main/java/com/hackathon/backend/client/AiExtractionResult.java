package com.hackathon.backend.client;

import java.time.LocalDate;

/** AI 분석 결과를 서비스 계층으로 넘기는 내부 값 객체. */
public record AiExtractionResult(
        String senderName,
        String relationship,
        LocalDate receivedDate,
        String occasion,
        String giftName,
        String categoryName,
        Integer amount
) {
}
