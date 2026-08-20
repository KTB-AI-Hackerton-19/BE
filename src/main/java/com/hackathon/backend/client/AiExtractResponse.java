package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 서비스 {@code POST /api/v1/agent/from-image} 응답.
 *
 * <p><b>사진 한 장에 여러 명이 들어 있는 경우</b>(봉투가 여러 개 찍힌 사진, 방명록, 단체 메시지 캡처)를 위해
 * payload를 목록으로 받는다. AI가 어떤 모양으로 주든 받아낼 수 있게 세 가지를 모두 허용한다.</p>
 *
 * <pre>
 * 1) 단건:      { "gift_data": { "payload": { "person_name": "김민수", ... } } }
 * 2) 배열:      { "gift_data": { "payload": [ {...}, {...} ] } }
 * 3) 중첩 배열: { "gift_data": { "payload": { "event": "결혼식", "people": [ {...}, {...} ] } } }
 * </pre>
 *
 * <p>3번은 공통값(경조사명·행사일·받은 날짜)이 부모에만 있고 사람별 값만 배열에 들어오는 형태라,
 * {@link #payloads()}에서 부모 값을 물려받아 평평하게 펼친다. 스펙이 아직 확정 전이라
 * 한쪽만 지원했다가 AI 팀이 다른 모양으로 주면 그날 바로 막히기 때문에 셋 다 열어뒀다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiExtractResponse(
        @JsonProperty("gift_data") Section giftData
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(
            String status,

            /** 객체 하나로 와도 원소 1개짜리 목록으로 받는다. */
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            @JsonAlias({"payloads", "gift_list", "gifts", "items", "people", "persons"})
            List<Payload> payload,

            String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("gift_name") String giftName,
            @JsonProperty("gift_price") Integer giftPrice,
            @JsonProperty("person_name") String personName,
            @JsonProperty("relationship") String relationship,
            @JsonProperty("received_at") LocalDate receivedAt,

            /** 선물 카테고리 이름("디저트"). 우리 categories 테이블 이름과 매칭되며, 모르는 값이면 "기타"로 폴백한다. */
            @JsonAlias({"category_name", "gift_category"}) String category,

            /** 받은 사람이 아니라 <b>보낸 사람</b>의 추정 나이·성별. 선물 추천의 참고값이다. */
            @JsonProperty("age") Integer age,
            @JsonProperty("gender") String gender,

            /** 0~1 신뢰도. 저장하지는 않고 낮을 때 로그로 남긴다. */
            @JsonProperty("confidence") Double confidence,

            /** 경조사명("결혼식", "장례식"). 경조사 여부 판정의 1순위 근거. */
            @JsonAlias({"event_name", "event_type", "eventType", "occasion"}) String event,

            /** 행사일. 축의금을 받은 날(received_at)과 다를 수 있어 따로 받는다. */
            @JsonProperty("event_date") @JsonAlias({"eventDate", "target_date", "targetDate"}) LocalDate eventDate,

            /** 중첩 형태(3번)일 때 사람별 값. 공통값은 이 payload(부모)에 있다. */
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            @JsonAlias({"persons", "guests", "entries", "gift_list", "gifts", "items"})
            List<Payload> people
    ) {

        /** 사람별 값이 비어 있는 자리만 부모(공통) 값으로 채운다. */
        Payload inheritFrom(Payload parent) {
            return new Payload(
                    giftName != null ? giftName : parent.giftName(),
                    giftPrice != null ? giftPrice : parent.giftPrice(),
                    personName,
                    relationship != null ? relationship : parent.relationship(),
                    receivedAt != null ? receivedAt : parent.receivedAt(),
                    category != null ? category : parent.category(),
                    age,
                    gender,
                    confidence != null ? confidence : parent.confidence(),
                    event != null ? event : parent.event(),
                    eventDate != null ? eventDate : parent.eventDate(),
                    null
            );
        }
    }

    /** gift_data가 실패 상태로 왔는지. 이 경우 payload가 있어도 믿지 않는다. */
    public boolean failed() {
        String status = giftData == null ? null : giftData.status();
        return status != null && (status.equalsIgnoreCase("failed") || status.equalsIgnoreCase("error"));
    }

    /** AI가 준 실패 사유. 없으면 null. */
    public String errorOrNull() {
        String error = giftData == null ? null : giftData.error();
        return error == null || error.isBlank() ? null : error;
    }

    /**
     * 사람 단위로 평평하게 펼친 목록. gift_data가 통째로 비었거나 실패 상태면 빈 목록이다.
     * <b>여기 담긴 개수가 곧 "사진에서 찾은 사람 수"</b>이고, 2 이상이면 여러 명 흐름으로 처리한다.
     */
    public List<Payload> payloads() {
        if (giftData == null || giftData.payload() == null) {
            return List.of();
        }
        List<Payload> flat = new ArrayList<>();
        for (Payload payload : giftData.payload()) {
            if (payload == null) {
                continue;
            }
            if (payload.people() == null || payload.people().isEmpty()) {
                flat.add(payload);
                continue;
            }
            for (Payload child : payload.people()) {
                if (child != null) {
                    flat.add(child.inheritFrom(payload));
                }
            }
        }
        return flat;
    }
}
