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
 * 2) records:   { "gift_data": { "payload": { "gift_name": "부조금", ..., "records": [ {...}, {...} ] } } }
 * 3) 배열:      { "gift_data": { "payload": [ {...}, {...} ] } }
 * </pre>
 *
 * <p><b>실제 AI 서비스가 쓰는 것은 2번</b>이다. 평면 필드는 "대표 1건"(가장 큰 금액)이고 전체는
 * {@code records}에 들어 있어서, records를 안 보면 사진에 몇 명이 있든 항상 1명만 저장된다.
 * 공통값(경조사명·행사일·받은 날짜)은 부모에만 있을 수 있어 {@link #payloads()}에서 물려받아 펼친다.</p>
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
            /** records 안에서는 이름이 {@code price}다(평면 필드만 {@code gift_price}). 둘 다 받는다. */
            @JsonProperty("gift_price") @JsonAlias({"price"}) Integer giftPrice,
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

            /** 내가 받은 것인지(received) 보낸 것인지(sent). 거래내역 캡처면 출금 건이 섞여 온다. */
            @JsonProperty("direction") String direction,

            /** AI가 저장 대상에서 뺀 항목이면 false. 명시가 없으면 저장 대상이다. */
            @JsonProperty("selected") Boolean selected,

            /**
             * 이미지에서 읽은 <b>전체 기록</b>. 평면 필드는 이 중 대표 1건일 뿐이라, 여러 명이면 여기를 봐야 한다.
             * AI 서비스의 {@code GiftData.records}가 이 자리다.
             */
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            @JsonProperty("records")
            @JsonAlias({"people", "persons", "guests", "entries", "gift_list", "gifts", "items"})
            List<Payload> people
    ) {

        /**
         * 이 기록을 "받은 마음"으로 저장할지. 우리 장부는 받은 것만 담으므로 출금(sent) 건은 뺀다.
         * 방향을 모르는(unknown/미지정) 건은 남긴다 — 빼버리면 사용자가 확인할 기회조차 없어진다.
         */
        boolean storable() {
            if (Boolean.FALSE.equals(selected)) {
                return false;
            }
            return direction == null || !direction.trim().equalsIgnoreCase("sent");
        }

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
                    direction != null ? direction : parent.direction(),
                    selected,
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
            List<Payload> children = new ArrayList<>();
            for (Payload child : payload.people()) {
                if (child != null && child.storable()) {
                    children.add(child.inheritFrom(payload));
                }
            }
            // 전부 걸러졌으면(예: 전부 출금 건) 대표 1건이라도 남긴다. 빈 결과는 폴백 더미로 이어져
            // "AI가 죽었다"는 잘못된 신호가 되기 때문이다.
            flat.addAll(children.isEmpty() ? List.of(payload) : children);
        }
        return flat;
    }
}
