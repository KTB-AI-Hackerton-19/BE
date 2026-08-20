package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 선물 추천 응답 ({@code POST /api/v1/agent/recommend}).
 *
 * <p>AI 명세의 {@code RecommendResponse}. 카드 목록이 아니라 <b>가격대 + 카테고리 + (있으면) 실제 상품</b>
 * 구조라, 화면의 추천 카드로 만드는 변환은 {@code RecommendationService}가 한다.</p>
 *
 * <p>모르는 필드는 무시한다 — AI 쪽 스키마가 아직 자주 바뀌어서(READY→SUCCESS 등)
 * 필드가 늘었다고 파싱이 깨지면 안 된다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiRecommendResponse(
        @JsonProperty("recommend_gift_info") Info recommendGiftInfo
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            /** SUCCESS / ERROR / SKIPPED */
            String status,
            @JsonProperty("recommend_gift") Gift recommendGift,
            Message message,
            String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gift(
            @JsonProperty("recommended_price_min") Integer priceMin,
            @JsonProperty("recommended_price_max") Integer priceMax,
            List<Category> categories,
            List<Product> products,
            String summary,
            String model,
            String source
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(
            String category,
            Integer score,
            String reason,
            @JsonProperty("product_examples") List<String> productExamples
    ) {
    }

    /** 실제 쇼핑몰 상품. 검색 결과가 없으면 빈 배열로 온다(실제로 자주 빈다). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            String title,
            String url,
            String source,
            String category,
            Integer price,
            @JsonProperty("price_verified") Boolean priceVerified,
            String reason
    ) {
    }

    /** 답례 인사 문구. 화면에 그대로 쓸 수 있는 품질로 온다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String tone, String content) {
    }

    /**
     * 추천 한 세트와, 그게 <b>진짜 AI 결과인지 더미 폴백인지</b>.
     *
     * <p>이 구분을 굳이 밖으로 들고 나가는 이유는 추천이 DB에 캐시되기 때문이다. AI가 잠깐 죽었을 때
     * 더미를 그냥 저장해버리면, 그 뒤로 캐시가 차 있으니 아무도 다시 만들지 않아서
     * <b>더미가 영구히 화면에 박힌다</b>. 폴백이라는 걸 알아야 나중에 조용히 진짜 추천으로 갈아끼울 수 있다.</p>
     */
    public record Result(List<Item> items, boolean fallback) {
    }

    /**
     * 서비스 계층이 쓰는 카드 한 장. AI 응답을 화면 모양으로 변환한 결과.
     *
     * <p>{@code emoji}는 AI가 주지 않으므로 보통 null이고, 서비스가 {@code aiCategory} 이름으로
     * 우리 카테고리를 찾아 채운다. {@code productUrl}은 실제 상품이 있을 때만 채워진다.</p>
     */
    public record Item(
            String emoji,
            String name,
            Integer amount,
            String tag,
            String reason,
            String productUrl,
            /** AI가 분류한 카테고리 이름("식품·디저트" 등). 우리 카테고리와 이름이 다를 수 있다. */
            String aiCategory,
            /**
             * 답례 인사 문구. AI는 추천 <b>한 세트에 하나</b>만 주므로 같은 세트의 카드에 모두 같은 값이 들어간다.
             * 화면에서는 하나만 보여주면 된다.
             */
            String thankYouMessage
    ) {
    }
}
