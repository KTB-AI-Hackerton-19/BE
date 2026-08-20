package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI 서비스 {@code POST /api/v1/agent/confirm} 요청·응답 DTO (OpenAPI 0.1.0 기준).
 *
 * <p>요청 본문은 snake_case, 그 안의 {@code calendar}(CalendarDraft)만 camelCase다.
 * AI 명세가 실제로 그렇게 되어 있어서(파이썬은 snake, JSON은 camel) 맞춰준다.</p>
 */
public final class AiConfirmDtos {

    private AiConfirmDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfirmRequest(
            @JsonProperty("workflow_id") String workflowId,
            @JsonProperty("gift_data") GiftData giftData,
            @JsonProperty("calendar") CalendarDraft calendar,
            @JsonProperty("approved") boolean approved,
            @JsonProperty("register_calendar") boolean registerCalendar,
            @JsonProperty("google_access_token") String googleAccessToken,
            @JsonProperty("calendar_id") String calendarId) {
    }

    /** gift_price는 명세상 0보다 커야 한다(exclusiveMinimum: 0). 0이나 null이면 422가 난다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GiftData(
            @JsonProperty("gift_name") String giftName,
            @JsonProperty("gift_price") Integer giftPrice,
            @JsonProperty("age") Integer age,
            @JsonProperty("gender") String gender,
            @JsonProperty("person_name") String personName,
            @JsonProperty("relationship") String relationship,
            @JsonProperty("received_at") LocalDate receivedAt,
            @JsonProperty("target_date") LocalDate targetDate,
            @JsonProperty("event") String event) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalendarDraft(
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("date") LocalDate date,
            @JsonProperty("startTime") String startTime,
            @JsonProperty("durationMinutes") Integer durationMinutes,
            @JsonProperty("timezone") String timezone,
            @JsonProperty("remindersMinutes") List<Integer> remindersMinutes,
            @JsonProperty("calendarId") String calendarId,
            @JsonProperty("targetDate") LocalDate targetDate,
            @JsonProperty("eventId") String eventId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfirmResponse(
            @JsonProperty("workflow_id") String workflowId,
            @JsonProperty("approved") Boolean approved,
            @JsonProperty("calendar_info") PreparedData calendarInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PreparedData(
            @JsonProperty("status") String status,
            @JsonProperty("payload") Map<String, Object> payload,
            @JsonProperty("error") String error) {

        public String payloadString(String key) {
            Object value = payload == null ? null : payload.get(key);
            return value == null ? null : String.valueOf(value);
        }

        public boolean payloadFlag(String key) {
            Object value = payload == null ? null : payload.get(key);
            return value instanceof Boolean flag && flag;
        }
    }
}
