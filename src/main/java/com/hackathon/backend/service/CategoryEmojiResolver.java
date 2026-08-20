package com.hackathon.backend.service;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI가 준 카테고리 이름으로 우리 카테고리의 이모지를 찾는다.
 *
 * <p>AI는 이모지를 주지 않고, 카테고리 이름도 자기 체계를 쓴다("식품·디저트" vs 우리 "디저트").
 * 그래서 이름이 정확히 같지 않아도 찾을 수 있게 세 단계로 본다.</p>
 *
 * <ol>
 *   <li>정확히 같은 이름</li>
 *   <li>한쪽이 다른 쪽을 포함 ("식품·디저트" ⊃ "디저트")</li>
 *   <li>구분자(·, /, 쉼표)로 쪼갠 조각끼리 비교 ("식품·디저트" → "식품", "디저트")</li>
 * </ol>
 *
 * <p>그래도 못 찾으면 기본 이모지를 쓴다. 매핑표를 하드코딩하지 않은 이유는,
 * 사용자가 카테고리를 자유롭게 추가할 수 있어 표가 금방 낡기 때문이다.</p>
 */
@Component
public class CategoryEmojiResolver {

    public static final String DEFAULT_EMOJI = "🎁";

    private final CategoryRepository categoryRepository;

    public CategoryEmojiResolver(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public String resolve(String username, String aiCategory) {
        if (aiCategory == null || aiCategory.isBlank()) {
            return DEFAULT_EMOJI;
        }
        String target = aiCategory.trim();
        List<Category> categories = categoryRepository.findByUser_UsernameOrderByDisplayOrderAscIdAsc(username);

        for (Category category : categories) {
            if (category.getName().equalsIgnoreCase(target)) {
                return orDefault(category.getEmoji());
            }
        }
        for (Category category : categories) {
            String name = category.getName();
            if (target.contains(name) || name.contains(target)) {
                return orDefault(category.getEmoji());
            }
        }
        for (String token : target.split("[·/,]")) {
            String piece = token.trim();
            if (piece.isEmpty()) {
                continue;
            }
            for (Category category : categories) {
                if (category.getName().contains(piece) || piece.contains(category.getName())) {
                    return orDefault(category.getEmoji());
                }
            }
        }
        return DEFAULT_EMOJI;
    }

    private String orDefault(String emoji) {
        return (emoji == null || emoji.isBlank()) ? DEFAULT_EMOJI : emoji;
    }
}
