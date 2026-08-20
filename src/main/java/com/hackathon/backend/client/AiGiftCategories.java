package com.hackathon.backend.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * AI 추천이 허용하는 카테고리 목록과, 우리 카테고리명 → AI 카테고리명 변환.
 *
 * <p>AI 서비스({@code app/services/recommendation_policy.py})는 이 11개 안에서만 추천한다.
 * 프롬프트에 목록을 박아두고 출력 스키마의 enum으로도 막아둬서, <b>목록 밖의 이름을 보내면 조용히 버려진다</b>
 * (요청이 실패하지 않고 그냥 무시되므로 알아채기 어렵다). 그래서 보내기 전에 여기서 맞춰 보낸다.</p>
 *
 * <p>{@link #ALLOWED}는 사용자의 카테고리가 하나도 안 맞을 때 쓰는 마지막 후보다. 평소에는
 * 사용자의 카테고리 목록에서 고른다. 참고로 실측상 <b>상품 검색이 잘 되는 카테고리와 그렇지 않은 카테고리</b>가
 * 갈린다 — 식품·커피·상품권은 실제 구매 링크가 붙고, 패션·뷰티는 자주 빈손이라 카드에 링크가 없다.</p>
 */
public final class AiGiftCategories {

    private AiGiftCategories() {
    }

    /** AI가 허용하는 전체 목록. */
    public static final List<String> ALLOWED = List.of(
            "식품·디저트", "패션·잡화",
            "커피·차", "뷰티·화장품",
            "상품권", "문화·취미",
            "생활용품", "건강·웰니스",
            "디지털 액세서리", "꽃·식물",
            "유아·아동");

    /** 우리 카테고리 이름(사용자가 추가한 것 포함)을 AI 이름으로 옮긴다. AI 쪽 CATEGORY_ALIASES와 같은 규칙. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("디저트", "식품·디저트"),
            Map.entry("식품", "식품·디저트"),
            Map.entry("음식", "식품·디저트"),
            Map.entry("식품/음료", "식품·디저트"),
            Map.entry("커피", "커피·차"),
            Map.entry("차", "커피·차"),
            Map.entry("패션", "패션·잡화"),
            Map.entry("잡화", "패션·잡화"),
            Map.entry("뷰티", "뷰티·화장품"),
            Map.entry("향수", "뷰티·화장품"),
            Map.entry("화장품", "뷰티·화장품"),
            Map.entry("문화", "문화·취미"),
            Map.entry("취미", "문화·취미"),
            Map.entry("건강", "건강·웰니스"),
            Map.entry("꽃", "꽃·식물"),
            Map.entry("식물", "꽃·식물"),
            Map.entry("전자기기", "디지털 액세서리"),
            Map.entry("디지털 기기", "디지털 액세서리"),
            Map.entry("유아", "유아·아동"),
            Map.entry("아동", "유아·아동"));

    /** 목록에 없는 이름이면 null. 보내봐야 버려지므로 아예 빼는 편이 낫다. */
    public static String normalize(String ourCategoryName) {
        if (ourCategoryName == null || ourCategoryName.isBlank()) {
            return null;
        }
        String name = ourCategoryName.trim();
        if (ALLOWED.contains(name)) {
            return name;
        }
        return ALIASES.get(name);
    }

    /**
     * 이번 세트에 보낼 카테고리를 <b>무작위로</b> 고른다. 단 {@code exclude}(직전 세트에 썼던 것)는 피한다.
     *
     * <p>무작위인 이유는 '다시 추천받기'가 예측 가능하면 재미가 없어서고, 직전 것을 빼는 이유는
     * 무작위만으로는 연달아 같은 조합이 나올 수 있어서다. 그러면 버튼을 눌러도 아무 일도 안 일어난 것처럼 보인다.
     * 뺄 것을 빼고 나면 후보가 모자랄 수 있는데, 그때는 뺐던 것 중에서 다시 채워 개수를 맞춘다
     * (카드 수가 줄어드는 쪽이 더 나쁘다).</p>
     *
     * @param pool  사용자의 카테고리 이름(우리 이름 그대로 넘겨도 된다 — 여기서 AI 이름으로 맞춘다)
     */
    public static List<String> pick(List<String> pool, Collection<String> exclude, int count, Random random) {
        List<String> candidates = new ArrayList<>(new LinkedHashSet<>(
                pool.stream().map(AiGiftCategories::normalize).filter(Objects::nonNull).toList()));
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(ALLOWED);
        }
        if (candidates.size() <= count) {
            // 후보가 모자라면 AI 허용 목록에서 채워 <b>항상 count개를 보낸다</b>.
            // 카테고리 하나당 상품 예시가 2개뿐이라, 적게 보내면 그대로 카드 수가 모자란다(실측: 1개 → 카드 2장).
            topUp(candidates, ALLOWED, List.of(), count, random);
            return candidates;
        }

        List<String> fresh = new ArrayList<>(candidates);
        fresh.removeAll(exclude == null ? List.of() : exclude);
        Collections.shuffle(fresh, random);

        List<String> picked = new ArrayList<>(fresh.subList(0, Math.min(count, fresh.size())));
        // 사용자 카테고리가 적으면(기본 5개) 3개를 고르는 순간 직전 것과 겹칠 수밖에 없다.
        // 그때는 AI가 허용하는 나머지 카테고리에서 먼저 채운다 — 직전 것을 다시 집는 것보다
        // 안 써본 카테고리를 보여주는 편이 '다시 추천받기'답다.
        topUp(picked, ALLOWED, exclude, count, random);
        topUp(picked, candidates, List.of(), count, random);
        return picked;
    }

    private static void topUp(List<String> picked, List<String> source, Collection<String> exclude,
                              int count, Random random) {
        if (picked.size() >= count) {
            return;
        }
        List<String> rest = new ArrayList<>(source);
        rest.removeAll(picked);
        rest.removeAll(exclude);
        Collections.shuffle(rest, random);
        picked.addAll(rest.subList(0, Math.min(count - picked.size(), rest.size())));
    }
}
