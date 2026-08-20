package com.hackathon.backend.client;

import java.time.LocalDate;

/**
 * AI가 읽어낸 <b>사람 한 명분</b>의 값. 사진 한 장에서 여러 명이 나오면 이 값이 사람 수만큼 만들어지고,
 * 전체는 {@link AiExtractionBatch}로 묶여 서비스 계층에 전달된다.
 *
 * <p>더미 폴백 여부는 사람별이 아니라 호출 단위라서 {@link AiExtractionBatch}에만 있다.</p>
 */
public record AiExtractionResult(
        String senderName,
        String relationship,
        LocalDate receivedDate,
        String occasion,
        String giftName,
        String categoryName,
        Integer amount,
        /** AI가 추정한 보낸 사람의 나이. 사람을 등록할 때 참고값으로 쓴다. */
        Integer age,
        /** AI가 추정한 보낸 사람의 성별. 문자열("male"/"남성")로 와서 enum으로 맞춘다. */
        String gender,
        /** 0~1 신뢰도. 낮으면 사용자가 먼저 확인해야 한다. */
        Double confidence,
        /** AI가 준 경조사명("결혼식"). 경조사가 아니면 null. */
        String eventName,
        /** 행사일. 받은 날짜와 다를 수 있다. */
        LocalDate eventDate
) {
}
