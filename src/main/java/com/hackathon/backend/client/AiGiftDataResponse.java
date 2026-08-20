package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 서비스 {@code POST /api/v1/agent/from-gift-data} 응답 (명세의 {@code GiftAgentResponse}).
 *
 * <p>{@code /from-image}와 <b>응답 모양이 완전히 같다</b>. 입력이 사진이냐 이미 아는 값이냐만 다르다.
 * 그래서 블록 파싱을 새로 쓰지 않고 기존 것을 그대로 재사용한다 —
 * {@code gift_data}는 {@link AiExtractResponse.Section}, {@code calendar_info}/{@code noti_info}는
 * {@link AiConfirmDtos.PreparedData}, {@code recommend_gift_info}는 {@link AiRecommendResponse.Info}.</p>
 *
 * <p>주의: 여기 담겨 오는 건 전부 <b>초안</b>이다. AI 서비스는 상태를 보관하지 않으므로
 * 이 응답만으로는 어디에도 저장되지 않는다. 실제 저장은 우리 DB(마음 기록 등록)와
 * {@code /confirm}(구글 캘린더)이 한다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiGiftDataResponse(
        @JsonProperty("gift_data") AiExtractResponse.Section giftData,
        @JsonProperty("calendar_info") AiConfirmDtos.PreparedData calendarInfo,
        @JsonProperty("noti_info") AiConfirmDtos.PreparedData notiInfo,
        @JsonProperty("recommend_gift_info") AiRecommendResponse.Info recommendGiftInfo,
        @JsonProperty("workflow_id") String workflowId,

        /** true면 AI가 "사용자 확인 후 /confirm으로 확정하라"는 뜻이다. */
        @JsonProperty("requires_confirmation") Boolean requiresConfirmation
) {
}
