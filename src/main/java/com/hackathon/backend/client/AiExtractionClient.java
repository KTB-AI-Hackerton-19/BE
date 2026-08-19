package com.hackathon.backend.client;

import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionClient.class);

    private static final String EXTRACT_PATH = "/api/v1/agent/from-image";

    private final RestClient restClient;
    private final String aiServiceUrl;
    /** false면 더미로 감추지 않고 그대로 실패시킨다(연동 디버깅용). */
    private final boolean fallbackEnabled;

    public AiExtractionClient(@Value("${ai.service.url}") String aiServiceUrl,
                              @Value("${ai.service.api-key}") String apiKey,
                              @Value("${ai.service.timeout-ms}") int timeoutMs,
                              @Value("${ai.service.fallback-enabled:true}") boolean fallbackEnabled) {
        this.aiServiceUrl = aiServiceUrl;
        this.fallbackEnabled = fallbackEnabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-API-KEY", apiKey == null ? "" : apiKey)
                .defaultHeader("ngrok-skip-browser-warning", "1")
                .build();
    }

    public AiExtractionResult extract(String imageReadUrl) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return fallbackOrFail("AI_SERVICE_URL이 설정되지 않았습니다.");
        }

        try {
            AiExtractResponse response = restClient.post()
                    .uri(trimTrailingSlash(aiServiceUrl) + EXTRACT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiExtractRequest(imageReadUrl))
                    .retrieve()
                    .body(AiExtractResponse.class);

            AiExtractResponse.Payload payload = response == null ? null : response.payloadOrNull();
            if (payload == null) {
                return fallbackOrFail("AI 응답에 gift_data가 없습니다.");
            }

            return new AiExtractionResult(
                    payload.personName(),
                    payload.relationship(),
                    payload.receivedAt(),
                    null,
                    payload.giftName(),
                    null,
                    payload.giftPrice(),
                    false,
                    null
            );
        } catch (RestClientResponseException e) {
            // 응답 본문까지 남긴다. AI 쪽 실패 원인("이미지 분석에 실패했습니다" 등)이 여기 들어 있다.
            return fallbackOrFail("AI %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            return fallbackOrFail("AI 호출 실패: " + e.getMessage());
        }
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 폴백이 켜져 있으면 더미에 실패 사유를 달아 돌려주고, 꺼져 있으면 그대로 502로 실패시킨다.
     * 연동 확인 중에는 {@code ai.service.fallback-enabled=false}로 두면 원인이 응답에 바로 나온다.
     */
    private AiExtractionResult fallbackOrFail(String reason) {
        if (!fallbackEnabled) {
            log.error("AI 분석 실패 — 폴백이 꺼져 있어 그대로 실패시킵니다. 사유: {}", reason);
            throw new CustomException(ErrorCode.AI_SERVICE_ERROR, reason);
        }
        log.error("AI 분석 실패 — 더미 결과로 대체합니다. 화면 값은 AI 결과가 아닙니다. 사유: {}", reason);
        return dummyResult(reason);
    }

    private AiExtractionResult dummyResult(String reason) {
        return new AiExtractionResult(
                "김민수",
                "친한 친구",
                LocalDate.now(),
                "내 생일",
                "스타벅스 케이크",
                "디저트",
                35000,
                true,
                reason
        );
    }
}
