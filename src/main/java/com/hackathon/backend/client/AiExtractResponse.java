package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;


@JsonIgnoreProperties(ignoreUnknown = true)
public record AiExtractResponse(
        @JsonProperty("gift_data") Section giftData
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            String status,
            Payload payload,
            String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("gift_name") String giftName,
            @JsonProperty("gift_price") Integer giftPrice,
            @JsonProperty("person_name") String personName,
            @JsonProperty("relationship") String relationship,
            @JsonProperty("received_at") LocalDate receivedAt
    ) {
    }

    /** gift_data가 통째로 비었거나 실패 상태면 null. */
    public Payload payloadOrNull() {
        if (giftData == null || giftData.payload() == null) {
            return null;
        }
        return giftData.payload();
    }
}
