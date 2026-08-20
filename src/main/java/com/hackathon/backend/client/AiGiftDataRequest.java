package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /api/v1/agent/from-gift-data} 요청 바디. 본문이 {@code gift_data} 한 겹으로 감싸여 있다.
 *
 * <p>안쪽 값은 {@code /confirm}이 쓰는 것과 같은 {@link AiConfirmDtos.GiftData}다 — AI 명세에서
 * 실제로 같은 스키마라, 따로 만들면 한쪽만 필드가 늘어나는 식으로 어긋난다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiGiftDataRequest(
        @JsonProperty("gift_data") AiConfirmDtos.GiftData giftData
) {
}
