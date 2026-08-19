package com.hackathon.backend.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 선물 추천 AI 서비스 클라이언트.
 *
 * <p>{@link AiExtractionClient}와 동일한 폴백 패턴: AI_SERVICE_URL 미설정/호출 실패 시 하드코딩 더미 3건을 반환해
 * 프론트가 추천 화면을 지금 바로 붙일 수 있게 한다.</p>
 */
@Component
public class AiRecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationClient.class);

    private final RestClient restClient;
    private final String aiServiceUrl;

    public AiRecommendationClient(@Value("${ai.service.url}") String aiServiceUrl,
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

    public List<AiRecommendResponse.Item> recommend(AiRecommendRequest request) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            log.info("AI_SERVICE_URL 미설정 — 더미 추천 결과 반환");
            return dummyItems(request.personName());
        }

        try {
            AiRecommendResponse response = restClient.post()
                    .uri(aiServiceUrl + "/recommendations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRecommendResponse.class);

            if (response == null || response.items() == null || response.items().isEmpty()) {
                return dummyItems(request.personName());
            }
            return response.items();
        } catch (RestClientException e) {
            log.warn("AI 추천 서비스 호출 실패, 더미 결과로 대체: {}", e.getMessage());
            return dummyItems(request.personName());
        }
    }

    private List<AiRecommendResponse.Item> dummyItems(String personName) {
        String who = (personName == null || personName.isBlank()) ? "이분" : shortName(personName);
        return List.of(
                new AiRecommendResponse.Item("☕", "스페셜티 드립백 세트", 32000, "취향 일치",
                        "%s님이 커피를 좋아하고, 받은 선물과 부담이 비슷해요.".formatted(who)),
                new AiRecommendResponse.Item("🍽️", "모바일 외식 상품권", 40000, "실패 확률 낮음",
                        "편하게 전하기 좋고 사용처가 다양해요."),
                new AiRecommendResponse.Item("🍪", "프리미엄 디저트 박스", 38000, "답례 추천",
                        "받았던 선물과 자연스럽게 이어지는 따뜻한 답례예요.")
        );
    }

    /** "김민수" → "민수" (디자인의 "민수님을 위한 추천" 문구용) */
    private String shortName(String name) {
        return name.length() >= 3 ? name.substring(1) : name;
    }
}
