package com.hackathon.backend.client;

import com.hackathon.backend.client.AiConfirmDtos.ConfirmRequest;
import com.hackathon.backend.client.AiConfirmDtos.ConfirmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 서비스의 {@code /api/v1/agent/confirm} 호출. 실제 구글 캘린더 등록은 AI 쪽이
 * 우리가 넘긴 {@code google_access_token}으로 대신 수행한다.
 *
 * <p>절대 예외를 밖으로 던지지 않는다. 캘린더 등록이 실패했다고 마음 기록 저장이 실패하면 안 되기 때문에,
 * 실패는 {@link CalendarRegistration#error()}에 담아 돌려주고 호출부가 판단한다.</p>
 */
@Component
public class AiCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(AiCalendarClient.class);

    private static final String CONFIRM_PATH = "/api/v1/agent/confirm";

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiCalendarClient(@Value("${ai.service.url}") String aiServiceUrl,
                            @Value("${ai.service.api-key}") String apiKey,
                            @Value("${ai.service.timeout-ms}") int timeoutMs) {
        this.aiServiceUrl = aiServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-API-KEY", apiKey == null ? "" : apiKey)
                .defaultHeader("ngrok-skip-browser-warning", "1")
                .build();
    }

    public CalendarRegistration confirm(ConfirmRequest request) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return CalendarRegistration.failed("AI_SERVICE_URL이 설정되지 않았습니다.");
        }
        try {
            ConfirmResponse response = restClient.post()
                    .uri(trimTrailingSlash(aiServiceUrl) + CONFIRM_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ConfirmResponse.class);

            AiConfirmDtos.PreparedData calendar = response == null ? null : response.calendarInfo();
            if (calendar == null) {
                return CalendarRegistration.failed("AI 응답에 calendar_info가 없습니다.");
            }
            // AI는 등록에 실패해도 200 + registerError로 돌려준다. status만 보면 실패를 놓친다.
            String registerError = calendar.payloadString("registerError");
            if (registerError != null) {
                return CalendarRegistration.failed(registerError);
            }
            if (!calendar.payloadFlag("registered")) {
                return CalendarRegistration.failed(
                        calendar.error() != null ? calendar.error() : "AI가 캘린더에 등록하지 않았습니다.");
            }
            return CalendarRegistration.ok(
                    calendar.payloadString("eventId"),
                    calendar.payloadString("htmlLink"));
        } catch (RestClientResponseException e) {
            log.warn("AI confirm 실패 {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return CalendarRegistration.failed("AI %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            log.warn("AI confirm 통신 실패: {}", e.getMessage());
            return CalendarRegistration.failed("AI 호출 실패: " + e.getMessage());
        }
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record CalendarRegistration(boolean registered, String eventId, String htmlLink, String error) {

        static CalendarRegistration ok(String eventId, String htmlLink) {
            return new CalendarRegistration(true, eventId, htmlLink, null);
        }

        static CalendarRegistration failed(String error) {
            return new CalendarRegistration(false, null, null, error);
        }
    }
}
