package com.hackathon.backend.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * 선물 추천 AI 클라이언트. {@code POST {AI_SERVICE_URL}/api/v1/agent/recommend}.
 *
 * <p>AI 응답은 "가격대 + 카테고리 + (있으면) 실제 상품"이라 화면의 추천 카드와 모양이 다르다.
 * 그 변환을 여기서 끝내고 서비스에는 카드 목록({@link AiRecommendResponse.Item})만 넘긴다.</p>
 *
 * <p>{@link AiExtractionClient}와 같은 폴백 정책: 실패하면 더미로 대체하되 <b>ERROR 로그에 사유를 남긴다</b>.
 * 조용히 더미가 나가면 "AI가 도는 줄" 착각하게 된다.</p>
 */
@Component
public class AiRecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationClient.class);

    private static final String RECOMMEND_PATH = "/api/v1/agent/recommend";

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

    public List<AiRecommendResponse.Item> recommend(AiRecommendRequest request, int limit) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return fallback("AI_SERVICE_URL이 설정되지 않았습니다.", request.personName());
        }

        try {
            AiRecommendResponse response = restClient.post()
                    .uri(trimTrailingSlash(aiServiceUrl) + RECOMMEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiRecommendResponse.class);

            AiRecommendResponse.Info info = response == null ? null : response.recommendGiftInfo();
            if (info == null || info.recommendGift() == null) {
                return fallback("응답에 recommend_gift가 없습니다. error=" + (info == null ? null : info.error()),
                        request.personName());
            }
            // HTTP 200이어도 블록 단위로 실패할 수 있다(status=ERROR/SKIPPED).
            if (info.status() != null && !"SUCCESS".equalsIgnoreCase(info.status())) {
                return fallback("status=%s error=%s".formatted(info.status(), info.error()), request.personName());
            }

            List<AiRecommendResponse.Item> items = toItems(info.recommendGift(), messageOf(info), limit);
            if (items.isEmpty()) {
                return fallback("추천 카테고리/상품이 비어 있습니다.", request.personName());
            }
            return items;
        } catch (RestClientResponseException e) {
            return fallback("AI %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()), request.personName());
        } catch (RestClientException e) {
            return fallback("호출 실패: " + e.getMessage(), request.personName());
        }
    }

    /**
     * AI 응답 → 추천 카드.
     *
     * <p>실제 상품({@code products})이 있으면 그걸 쓰고, 비어 있으면(자주 그렇다)
     * 카테고리의 {@code product_examples}와 추천 가격대 중앙값으로 채운다.</p>
     */
    private List<AiRecommendResponse.Item> toItems(AiRecommendResponse.Gift gift, String message, int limit) {
        List<AiRecommendResponse.Item> items = new ArrayList<>();

        List<AiRecommendResponse.Product> products = gift.products() == null ? List.of() : gift.products();
        for (AiRecommendResponse.Product product : products) {
            if (items.size() >= limit) {
                break;
            }
            items.add(new AiRecommendResponse.Item(
                    null, product.title(), product.price(), null, product.reason(),
                    product.url(), product.category(), message));
        }

        List<AiRecommendResponse.Category> categories = gift.categories() == null ? List.of() : gift.categories();
        List<AiRecommendResponse.Category> sorted = categories.stream()
                .sorted(Comparator.comparing(
                        (AiRecommendResponse.Category c) -> c.score() == null ? 0 : c.score()).reversed())
                .toList();

        for (AiRecommendResponse.Category category : sorted) {
            List<String> examples = category.productExamples() == null ? List.of() : category.productExamples();
            // 예시가 하나도 없으면 카테고리 이름 자체를 후보로 쓴다.
            List<String> names = examples.isEmpty() ? List.of(category.category()) : examples;
            for (String name : names) {
                if (items.size() >= limit) {
                    return items;
                }
                // 실제 상품이 아니라 예시 이름이므로 구매 링크는 없다.
                items.add(new AiRecommendResponse.Item(
                        null, name, midPrice(gift), tagOf(category.score()), category.reason(),
                        null, category.category(), message));
            }
        }
        return items;
    }

    /** 답례 인사 문구. 없으면 null — 화면에서 그 영역을 숨기면 된다. */
    private String messageOf(AiRecommendResponse.Info info) {
        AiRecommendResponse.Message message = info.message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            return null;
        }
        return message.content().trim();
    }

    /** 상품 가격이 없을 때 쓰는 대표 금액. 추천 가격대의 중앙값. */
    private Integer midPrice(AiRecommendResponse.Gift gift) {
        Integer min = gift.priceMin();
        Integer max = gift.priceMax();
        if (min == null && max == null) {
            return null;
        }
        if (min == null) {
            return max;
        }
        if (max == null) {
            return min;
        }
        return (min + max) / 2;
    }

    /** 카테고리 점수 → 화면 뱃지. 디자인의 고정 3종에 맞춘다. */
    private String tagOf(Integer score) {
        if (score == null) {
            return "실패 확률 낮음";
        }
        if (score >= 90) {
            return "취향 일치";
        }
        if (score >= 70) {
            return "답례 추천";
        }
        return "실패 확률 낮음";
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private List<AiRecommendResponse.Item> fallback(String reason, String personName) {
        log.error("AI 추천 실패 — 더미로 대체합니다. 화면 값은 AI 결과가 아닙니다. 사유: {}", reason);
        return dummyItems(personName);
    }

    private List<AiRecommendResponse.Item> dummyItems(String personName) {
        String who = (personName == null || personName.isBlank()) ? "이분" : shortName(personName);
        String message = "%s님, 챙겨주신 마음 덕분에 큰 힘이 됐어요. 작은 정성이지만 고마운 마음을 담아 준비했습니다."
                .formatted(who);
        return List.of(
                new AiRecommendResponse.Item("☕", "스페셜티 드립백 세트", 32000, "취향 일치",
                        "%s님이 커피를 좋아하고, 받은 선물과 부담이 비슷해요.".formatted(who), null, null, message),
                new AiRecommendResponse.Item("🍽️", "모바일 외식 상품권", 40000, "실패 확률 낮음",
                        "편하게 전하기 좋고 사용처가 다양해요.", null, null, message),
                new AiRecommendResponse.Item("🍪", "프리미엄 디저트 박스", 38000, "답례 추천",
                        "받았던 선물과 자연스럽게 이어지는 따뜻한 답례예요.", null, null, message)
        );
    }

    /** "김민수" → "민수" (디자인의 "민수님을 위한 추천" 문구용) */
    private String shortName(String name) {
        return name.length() >= 3 ? name.substring(1) : name;
    }
}
