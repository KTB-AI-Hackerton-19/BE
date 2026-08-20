package com.hackathon.backend.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI의 추천 응답({@code recommend_gift_info}) → 화면의 추천 카드 목록.
 *
 * <p>AI는 "가격대 + 카테고리 + (있으면) 실제 상품" 구조로 주고 화면은 카드 목록을 원한다. 그 변환이
 * {@code /recommend}와 {@code /from-gift-data} 두 곳에서 똑같이 필요해서 여기로 뺐다 —
 * 두 벌로 두면 한쪽만 고쳐져 같은 AI 응답이 화면마다 다르게 보이게 된다.</p>
 */
final class GiftRecommendationMapper {

    private GiftRecommendationMapper() {
    }

    /**
     * <p>실제 상품({@code products})이 있으면 그걸 쓰고, 비어 있으면(자주 그렇다)
     * 카테고리의 {@code product_examples}와 추천 가격대 중앙값으로 채운다.</p>
     */
    static List<AiRecommendResponse.Item> toItems(AiRecommendResponse.Gift gift, String message, int limit) {
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
    static String messageOf(AiRecommendResponse.Info info) {
        AiRecommendResponse.Message message = info.message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            return null;
        }
        return message.content().trim();
    }

    /** 상품 가격이 없을 때 쓰는 대표 금액. 추천 가격대의 중앙값. */
    private static Integer midPrice(AiRecommendResponse.Gift gift) {
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
    private static String tagOf(Integer score) {
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
}
