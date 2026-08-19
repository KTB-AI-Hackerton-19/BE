package com.hackathon.backend.service;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.dto.category.CategoryRequest;
import com.hackathon.backend.dto.category.CategoryResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.CategoryRepository;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "blue";
    /** 이름이 매칭 안 될 때 폴백으로 쓰는 카테고리 */
    public static final String FALLBACK_CATEGORY_NAME = "기타";

    private final CategoryRepository categoryRepository;
    private final GiftRecordRepository giftRecordRepository;

    public CategoryService(CategoryRepository categoryRepository, GiftRecordRepository giftRecordRepository) {
        this.categoryRepository = categoryRepository;
        this.giftRecordRepository = giftRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        String username = SecurityUtils.getCurrentUsername();
        Map<Long, Long> counts = new HashMap<>();
        giftRecordRepository.countGroupedByCategory(username)
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));

        List<Category> categories = includeInactive
                ? categoryRepository.findAllByOrderByDisplayOrderAscIdAsc()
                : categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();

        return categories.stream()
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.DUPLICATE_CATEGORY);
        }
        int order = request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder();
        Category category = new Category(
                name,
                blankToDefault(request.emoji(), DEFAULT_EMOJI),
                blankToDefault(request.color(), DEFAULT_COLOR),
                order,
                request.active() == null || request.active()
        );
        categoryRepository.save(category);
        return CategoryResponse.from(category, 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        String name = request.name() != null ? request.name().trim() : null;
        if (name != null && !name.equals(category.getName()) && categoryRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.DUPLICATE_CATEGORY);
        }
        category.update(name, request.emoji(), request.color(), request.displayOrder(), request.active());

        String username = SecurityUtils.getCurrentUsername();
        long count = giftRecordRepository.countByUser_UsernameAndCategory_Id(username, category.getId());
        return CategoryResponse.from(category, count);
    }

    /**
     * categoryId 우선, 없으면 categoryName으로 조회. 둘 다 못 찾으면 "기타"로 폴백한다.
     * (AI가 우리가 모르는 카테고리 이름을 뱉어도 저장이 실패하지 않게 하기 위함)
     */
    @Transactional(readOnly = true)
    public Category resolve(Long categoryId, String categoryName) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            return categoryRepository.findByName(categoryName.trim())
                    .orElseGet(this::fallbackCategory);
        }
        return null;
    }

    /** 저장 시점에 카테고리를 반드시 정해야 할 때(등록/AI 추출) 쓰는 버전 — null 대신 "기타"를 돌려준다. */
    @Transactional(readOnly = true)
    public Category resolveOrFallback(Long categoryId, String categoryName) {
        Category resolved = resolve(categoryId, categoryName);
        return resolved != null ? resolved : fallbackCategory();
    }

    private Category fallbackCategory() {
        return categoryRepository.findByName(FALLBACK_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .findFirst()
                        .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND)));
    }

    private int nextDisplayOrder() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .mapToInt(Category::getDisplayOrder)
                .max()
                .orElse(0) + 10;
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
