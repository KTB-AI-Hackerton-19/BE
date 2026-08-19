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

/**
 * 선물 이미지 분석 AI 서비스 클라이언트.
 *
 * <p>계약: {@code POST {AI_SERVICE_URL}/extract}, 요청 바디는 {@code {"imageUrl": "<presigned GET URL>"}} 하나.
 * AI 서비스가 아직 없거나(AI_SERVICE_URL 미설정) 호출이 실패하면 하드코딩 더미 결과로 폴백해서
 * 프론트가 지금 당장 전체 흐름을 붙여볼 수 있게 한다.</p>
 */
@Component
public class AiExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionClient.class);

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
                    .uri(aiServiceUrl + "/extract")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiExtractRequest(imageReadUrl))
                    .retrieve()
                    .body(AiExtractResponse.class);

            if (response == null) {
                return dummyResult();
            }

            return new AiExtractionResult(
                    response.senderName(),
                    response.relationship(),
                    response.receivedDate(),
                    response.occasion(),
                    response.giftName(),
                    response.category(),
                    response.amount()
            );
        } catch (RestClientException e) {
            log.warn("AI 분석 서비스 호출 실패, 더미 결과로 대체: {}", e.getMessage());
            return dummyResult();
        }
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
