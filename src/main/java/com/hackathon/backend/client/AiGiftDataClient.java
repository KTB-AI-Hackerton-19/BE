package com.hackathon.backend.client;

import java.util.List;
import java.util.Map;
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
 * 사진 없이 <b>이미 아는 값</b>으로 AI 준비 작업을 돌린다.
 * {@code POST {AI_SERVICE_URL}/api/v1/agent/from-gift-data}.
 *
 * <p>{@link AiExtractionClient}(사진 → 값)와 입력만 다르고 나오는 것은 같다 — 기록·캘린더·알림 초안과 추천.
 * 다만 우리 흐름에서 <b>기록·알림은 이미 우리 DB가 만들고</b> 캘린더는 {@code /confirm}이 실제로 등록하므로,
 * 실질적으로 새로 얻는 값은 <b>추천과 답례 메시지</b>다. 나머지 두 초안은 AI가 준 그대로 통과시킨다
 * (우리가 쓸 곳이 정해지지 않아 스키마를 지어내지 않는다).</p>
 *
 * <p><b>여기서는 더미로 폴백하지 않는다.</b> 추천/추출 쪽 폴백은 "화면이 비면 안 된다"는 이유가 있지만,
 * 이 경로는 아직 화면에 붙어 있지 않다. 지금 더미를 깔아두면 나중에 붙일 때 AI가 죽어 있어도
 * 잘 도는 것처럼 보인다 — 연동일에 가장 사람을 헷갈리게 하는 실패 방식이라 실패는 실패로 돌려준다.</p>
 */
@Component
public class AiGiftDataClient {

    private static final Logger log = LoggerFactory.getLogger(AiGiftDataClient.class);

    private static final String PREPARE_PATH = "/api/v1/agent/from-gift-data";

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiGiftDataClient(@Value("${ai.service.url}") String aiServiceUrl,
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

    /**
     * @param limit 추천 카드 최대 개수
     * @return 실패해도 예외를 던지지 않고 {@link Preparation#error()}에 사유를 담아 돌려준다.
     *         호출부가 "추천 없이 등록은 계속"을 고를 수 있어야 하기 때문이다.
     */
    public Preparation prepare(AiConfirmDtos.GiftData giftData, int limit) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return failed("AI_SERVICE_URL이 설정되지 않았습니다.");
        }

        try {
            AiGiftDataResponse response = restClient.post()
                    .uri(trimTrailingSlash(aiServiceUrl) + PREPARE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiGiftDataRequest(giftData))
                    .retrieve()
                    .body(AiGiftDataResponse.class);

            if (response == null) {
                return failed("AI 응답이 비어 있습니다.");
            }

            // 블록별로 따로 실패할 수 있다. 추천이 죽어도 캘린더 초안은 살아 있을 수 있으므로
            // 통째로 실패시키지 않고 있는 것만 담는다.
            return new Preparation(
                    recommendations(response.recommendGiftInfo(), limit),
                    thankYouMessage(response.recommendGiftInfo()),
                    payloadOf(response.calendarInfo()),
                    payloadOf(response.notiInfo()),
                    response.workflowId(),
                    Boolean.TRUE.equals(response.requiresConfirmation()),
                    blockError(response));
        } catch (RestClientResponseException e) {
            return failed("AI %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            return failed("AI 호출 실패: " + e.getMessage());
        }
    }

    private List<AiRecommendResponse.Item> recommendations(AiRecommendResponse.Info info, int limit) {
        if (info == null || info.recommendGift() == null) {
            return List.of();
        }
        // HTTP 200이어도 블록 단위로 실패할 수 있다(status=ERROR/SKIPPED).
        if (info.status() != null && !"SUCCESS".equalsIgnoreCase(info.status())) {
            log.warn("AI 추천 블록 실패 status={} error={}", info.status(), info.error());
            return List.of();
        }
        return GiftRecommendationMapper.toItems(info.recommendGift(), GiftRecommendationMapper.messageOf(info), limit);
    }

    private String thankYouMessage(AiRecommendResponse.Info info) {
        return info == null ? null : GiftRecommendationMapper.messageOf(info);
    }

    private Map<String, Object> payloadOf(AiConfirmDtos.PreparedData data) {
        return data == null || data.payload() == null ? Map.of() : data.payload();
    }

    /** 블록 중 하나라도 사유를 남겼으면 그걸 올린다. 없으면 null(=성공). */
    private String blockError(AiGiftDataResponse response) {
        for (String error : List.of(
                errorOf(response.giftData()),
                errorOf(response.calendarInfo()),
                errorOf(response.notiInfo()),
                response.recommendGiftInfo() == null ? "" : nullToEmpty(response.recommendGiftInfo().error()))) {
            if (!error.isBlank()) {
                log.warn("AI from-gift-data 블록 오류: {}", error);
                return error;
            }
        }
        return null;
    }

    private String errorOf(AiConfirmDtos.PreparedData data) {
        return data == null ? "" : nullToEmpty(data.error());
    }

    private String errorOf(AiExtractResponse.Section section) {
        return section == null ? "" : nullToEmpty(section.error());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Preparation failed(String reason) {
        log.error("AI from-gift-data 실패 — 추천 없이 응답합니다. 사유: {}", reason);
        return new Preparation(List.of(), null, Map.of(), Map.of(), null, false, reason);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * AI가 준비해준 것들.
     *
     * @param calendarDraft AI가 제안한 캘린더 일정 초안. <b>등록된 것이 아니다</b> — 실제 등록은 {@code /confirm}.
     * @param notiDraft     AI가 제안한 알림 초안. 우리 답례 알림(ReminderTask)과는 별개다.
     * @param workflowId    {@code /confirm}으로 확정할 때 그대로 돌려줘야 하는 값.
     * @param error         전부 또는 일부가 실패했을 때의 사유. 성공이면 null.
     */
    public record Preparation(
            List<AiRecommendResponse.Item> recommendations,
            String thankYouMessage,
            Map<String, Object> calendarDraft,
            Map<String, Object> notiDraft,
            String workflowId,
            boolean requiresConfirmation,
            String error) {
    }
}
