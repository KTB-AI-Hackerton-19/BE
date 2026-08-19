package com.hackathon.backend.service;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.GiftKind;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.category.CategoryRequest;
import com.hackathon.backend.dto.category.CategoryUpdateRequest;
import com.hackathon.backend.dto.category.CategoryResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.CategoryRepository;
import com.hackathon.backend.repository.GiftRecordRepository;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.SecurityUtils;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카테고리는 <b>사용자별</b>로 관리한다.
 *
 * <p>가입 시점에 기본 7종이 그 사용자 것으로 복제되고, 이후 추가·수정·삭제는 전부 자기 것에만 적용된다.
 * 예전에는 전역 테이블이라 한 사람이 "디저트"의 이모지를 바꾸면 모두에게 반영되는 문제가 있었다.</p>
 */
@Service
public class CategoryService {

    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "blue";
    /** 이름이 매칭 안 될 때 폴백으로 쓰는 카테고리. 이것만은 삭제할 수 없다. */
    public static final String FALLBACK_CATEGORY_NAME = "기타";

    /** 가입 시 복제해줄 기본 카테고리. 이름, 이모지, 색, 정렬순서, 속한 탭. */
    private record Seed(String name, String emoji, String color, int displayOrder, GiftKind kind) {
    }

    /**
     * 화면 상단 탭이 kind로 갈린다 — [선물] 탭에는 GIFT가, [경조사] 탭에는 경사·조사가 모인다.
     * displayOrder를 탭별로 100단위씩 띄워둬서 나중에 사이에 끼워 넣기 쉽다.
     */
    private static final List<Seed> DEFAULTS = List.of(
            // ── 선물 탭
            new Seed("디저트", "🍰", "mint", 10, GiftKind.GIFT),
            new Seed("꽃·식물", "💐", "pink", 20, GiftKind.GIFT),
            new Seed("패션·잡화", "👜", "gold", 30, GiftKind.GIFT),
            new Seed("상품권", "🎫", "mint", 40, GiftKind.GIFT),
            new Seed("생활용품", "🕯️", "pink", 50, GiftKind.GIFT),
            new Seed(FALLBACK_CATEGORY_NAME, "🎁", "blue", 60, GiftKind.GIFT)
            // 경조사 탭은 기본값을 두지 않는다. "내 결혼식"처럼 사용자의 실제 이벤트가 들어갈 자리라
            // 빈 껍데기 "결혼식"을 미리 만들어두면 오히려 헷갈린다. 화면에서 "+ 새 경조사"로 만든다.
    );

    private static final long[] EMPTY_AGG = {0L, 0L};

    private final CategoryRepository categoryRepository;
    private final GiftRecordRepository giftRecordRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, GiftRecordRepository giftRecordRepository,
                           UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.giftRecordRepository = giftRecordRepository;
        this.userRepository = userRepository;
    }

    /**
     * 사용자에게 기본 카테고리를 깔아준다. 가입 직후 한 번 호출한다.
     * 이미 같은 이름이 있으면 건너뛰므로 여러 번 불려도 중복되지 않는다.
     */
    @Transactional
    public void provisionDefaults(User user) {
        List<Category> missing = DEFAULTS.stream()
                .filter(seed -> !categoryRepository.existsByUser_UsernameAndName(user.getUsername(), seed.name()))
                .map(seed -> new Category(user, seed.name(), seed.emoji(), seed.color(), seed.displayOrder(), true,
                        seed.kind()))
                .toList();
        if (!missing.isEmpty()) {
            categoryRepository.saveAll(missing);
        }
    }

    /**
     * 카테고리 목록. {@code kind}로 탭을 고르면 그 탭의 카테고리만 나온다
     * (EVENT/경조사 → 경사+조사, GIFT/선물 → 선물, 생략 → 전체).
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive, String kind) {
        String username = SecurityUtils.getCurrentUsername();
        List<GiftKind> kinds = GiftKind.parseFilter(kind);
        Map<Long, long[]> counts = new HashMap<>();   // categoryId → [건수, 금액합]
        Map<Long, LocalDate> latest = new HashMap<>();
        giftRecordRepository.aggregateByCategory(username).forEach(row -> {
            Long cid = (Long) row[0];
            counts.put(cid, new long[]{(Long) row[1], ((Number) row[2]).longValue()});
            latest.put(cid, (LocalDate) row[3]);
        });

        List<Category> categories = includeInactive
                ? categoryRepository.findByUser_UsernameAndKindInOrderByDisplayOrderAscIdAsc(username, kinds)
                : categoryRepository.findByUser_UsernameAndKindInAndActiveTrueOrderByDisplayOrderAscIdAsc(username, kinds);

        return categories.stream()
                .map(c -> {
                    long[] agg = counts.getOrDefault(c.getId(), EMPTY_AGG);
                    return CategoryResponse.from(c, agg[0], agg[1], latest.get(c.getId()));
                })
                // 선물은 지정한 순서대로, 경조사는 최근 이벤트가 위로 오게 한다.
                // (경조사는 시간이 지날수록 쌓이므로 최신순이 아니면 오래된 게 계속 위에 남는다)
                .sorted(Comparator
                        .comparing(CategoryResponse::event)
                        .thenComparing(r -> r.event() ? null : r.displayOrder(),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(CategoryResponse::latestDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String name = request.name().trim();
        if (categoryRepository.existsByUser_UsernameAndName(username, name)) {
            throw new CustomException(ErrorCode.DUPLICATE_CATEGORY);
        }
        int order = request.displayOrder() != null ? request.displayOrder() : nextDisplayOrder(username);
        Category category = new Category(
                user,
                name,
                blankToDefault(request.emoji(), DEFAULT_EMOJI),
                blankToDefault(request.color(), DEFAULT_COLOR),
                order,
                request.active() == null || request.active(),
                GiftKind.parseOrDefault(request.kind())
        );
        categoryRepository.save(category);
        return CategoryResponse.from(category, 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        Category category = findOwned(id, username);

        String name = request.name() != null ? request.name().trim() : null;
        if (name != null && !name.equals(category.getName())
                && categoryRepository.existsByUser_UsernameAndName(username, name)) {
            throw new CustomException(ErrorCode.DUPLICATE_CATEGORY);
        }
        category.update(name, request.emoji(), request.color(), request.displayOrder(), request.active(),
                request.kind() == null ? null : GiftKind.parseOrDefault(request.kind()));

        long count = giftRecordRepository.countByUser_UsernameAndCategory_Id(username, category.getId());
        return CategoryResponse.from(category, count);
    }

    /**
     * 카테고리 삭제. 그 카테고리로 저장된 기록은 지우지 않고 <b>"기타"로 옮긴 뒤</b> 카테고리만 없앤다.
     * 기록까지 함께 지우면 카테고리를 정리하다 실수로 마음 기록을 날리게 되기 때문이다.
     *
     * <p>"기타"는 폴백 대상이라 삭제할 수 없다. 잠깐 안 쓰고 싶은 것뿐이라면
     * {@code PATCH /api/categories/{id}} 로 {@code active:false} 를 주면 목록에서만 숨겨진다.</p>
     *
     * @return "기타"로 옮겨진 기록 수
     */
    @Transactional
    public int delete(Long id) {
        String username = SecurityUtils.getCurrentUsername();
        Category category = findOwned(id, username);

        if (FALLBACK_CATEGORY_NAME.equals(category.getName())) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "'%s'는 분류가 애매한 기록이 모이는 곳이라 삭제할 수 없습니다.".formatted(FALLBACK_CATEGORY_NAME));
        }

        Category fallback = fallbackCategory(username);
        List<GiftRecord> moved = giftRecordRepository.findByUser_UsernameAndCategory_Id(username, id);
        moved.forEach(record -> record.changeCategory(fallback));

        categoryRepository.delete(category);
        return moved.size();
    }

    /**
     * categoryId 우선, 없으면 categoryName으로 조회. 둘 다 못 찾으면 "기타"로 폴백한다.
     * (AI가 우리가 모르는 카테고리 이름을 뱉어도 저장이 실패하지 않게 하기 위함)
     */
    @Transactional(readOnly = true)
    public Category resolve(Long categoryId, String categoryName) {
        String username = SecurityUtils.getCurrentUsername();
        if (categoryId != null) {
            return findOwned(categoryId, username);
        }
        if (categoryName != null && !categoryName.isBlank()) {
            return categoryRepository.findByUser_UsernameAndName(username, categoryName.trim())
                    .orElseGet(() -> fallbackCategory(username));
        }
        return null;
    }

    /** 저장 시점에 카테고리를 반드시 정해야 할 때(등록/AI 추출) 쓰는 버전 — null 대신 "기타"를 돌려준다. */
    @Transactional(readOnly = true)
    public Category resolveOrFallback(Long categoryId, String categoryName) {
        Category resolved = resolve(categoryId, categoryName);
        return resolved != null ? resolved : fallbackCategory(SecurityUtils.getCurrentUsername());
    }

    private Category findOwned(Long id, String username) {
        return categoryRepository.findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Category fallbackCategory(String username) {
        return categoryRepository.findByUser_UsernameAndName(username, FALLBACK_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository
                        .findByUser_UsernameAndActiveTrueOrderByDisplayOrderAscIdAsc(username).stream()
                        .findFirst()
                        .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND)));
    }

    private int nextDisplayOrder(String username) {
        return categoryRepository.findByUser_UsernameOrderByDisplayOrderAscIdAsc(username).stream()
                .mapToInt(Category::getDisplayOrder)
                .max()
                .orElse(0) + 10;
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
