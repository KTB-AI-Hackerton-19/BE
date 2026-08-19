package com.hackathon.backend.client;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionClient.class);

    private static final String EXTRACT_PATH = "/api/v1/agent/from-image";

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiExtractionClient(@Value("${ai.service.url}") String aiServiceUrl,
                              @Value("${ai.service.api-key}") String apiKey,
                              @Value("${ai.service.timeout-ms}") int timeoutMs) {
        this.aiServiceUrl = aiServiceUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-API-KEY", apiKey == null ? "" : apiKey)
                .build();
    }

    public AiExtractionResult extract(String imageReadUrl) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            log.info("AI_SERVICE_URL 미설정 — 더미 추출 결과 반환");
            return dummyResult();
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
                log.warn("AI 응답에 gift_data가 없어 더미 결과로 대체합니다.");
                return dummyResult();
            }

            return new AiExtractionResult(
                    payload.personName(),
                    payload.relationship(),
                    payload.receivedAt(),
                    null,
                    payload.giftName(),
                    null,
                    payload.giftPrice()
            );
        } catch (RestClientException e) {
            log.warn("AI 분석 서비스 호출 실패, 더미 결과로 대체: {}", e.getMessage());
            return dummyResult();
        }
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private AiExtractionResult dummyResult() {
        return new AiExtractionResult(
                "김민수",
                "친한 친구",
                LocalDate.now(),
                "내 생일",
                "스타벅스 케이크",
                "디저트",
                35000
        );
    }
}
