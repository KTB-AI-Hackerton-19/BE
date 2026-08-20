package com.hackathon.backend.support;

import com.hackathon.backend.domain.EventCategory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI가 준 텍스트(경조사명·받은 이유·선물명)를 보고 <b>7종 경조사 유형 중 무엇인지</b>를 판정한다.
 *
 * <p>AI에게 {@link EventCategory}를 직접 뱉으라고 요구하지 않는 이유는, 프롬프트가 바뀌거나 모델이 바뀌면
 * "WEDDING" 대신 "결혼식"이 오는 식으로 쉽게 어긋나기 때문이다. 자유 텍스트를 받아
 * 백엔드에서 한 번 정규화하면 AI 쪽 출력이 흔들려도 분류는 유지된다.</p>
 */
public final class EventClassifier {

    /**
     * 유형별 판정 단어. 조사(위로할 일)를 경사보다 먼저 본다 — 조문을 축하로 잘못 분류하는 쪽이
     * 훨씬 치명적이기 때문이다. {@link LinkedHashMap}이라 선언 순서가 곧 판정 우선순위다.
     */
    private static final Map<EventCategory, List<String>> EVENT_WORDS = new LinkedHashMap<>();

    static {
        EVENT_WORDS.put(EventCategory.FUNERAL, List.of(
                "장례", "조의", "부의", "빈소", "발인", "상가", "문상", "조문", "근조", "별세", "부고", "상주", "위로"));
        EVENT_WORDS.put(EventCategory.MEMORIAL_SERVICE, List.of(
                "제사", "탈상", "삼우", "추도", "추모", "기일"));
        EVENT_WORDS.put(EventCategory.WEDDING, List.of(
                "결혼", "웨딩", "혼례", "화촉", "신혼", "축의"));
        EVENT_WORDS.put(EventCategory.CHILDBIRTH, List.of(
                "출산", "돌잔치", "첫돌", "백일"));
        EVENT_WORDS.put(EventCategory.LONGEVITY_BIRTHDAY, List.of(
                "회갑", "환갑", "칠순", "팔순", "고희", "수연"));
        EVENT_WORDS.put(EventCategory.EMPLOYMENT_PROMOTION, List.of(
                "승진", "영전", "취임", "취업", "입사"));
        EVENT_WORDS.put(EventCategory.OPENING_MOVING, List.of(
                "개업", "개원", "창업", "이사"));
    }

    /**
     * 선물명만 보고도 경조사가 확실한 말들. 일반 선물 이름으로는 절대 쓰이지 않는다.
     * ("생일 축하 케이크"가 경조사로 넘어가는 걸 막으려고 선물명은 원래 판정에서 뺐는데,
     *  축의금·부의금처럼 그 자체가 경조사인 것까지 놓치면 반대로 전부 '기타 선물'이 된다.)
     */
    private static final List<String> MONEY_WORDS = List.of(
            "축의금", "축의", "부의금", "부의", "조의금", "조의", "부조금", "부조", "조위금", "근조", "화환");

    private EventClassifier() {
    }

    /**
     * 넘긴 텍스트 중 하나라도 경조사 단어에 걸리면 그 유형을, 안 걸리면 {@code null}을 돌려준다.
     * {@code null}이면 일반 선물(GIFT)로 처리하면 된다.
     *
     * <p>"부조"처럼 경조사인 건 분명해도 구체 유형이 안 갈리는 말은 일부러 목록에 넣지 않았다 — 특정 유형을
     * 지어내는 것보다 사용자가 확인 폼에서 직접 고르는 편이 낫다(확정 전 DRAFT라 어차피 검토를 거친다).</p>
     */
    public static EventCategory classify(String... texts) {
        String haystack = join(texts);
        if (haystack.isEmpty()) {
            return null;
        }
        for (Map.Entry<EventCategory, List<String>> entry : EVENT_WORDS.entrySet()) {
            if (containsAny(haystack, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 경조사 카드에 보여줄 행사명을 정한다. AI가 준 경조사명을 그대로 쓰고, 없으면 유형 라벨로 대신한다.
     */
    public static String eventName(EventCategory category, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return category != null ? category.getLabel() : null;
    }

    /**
     * 선물명 전용 판정. {@link #MONEY_WORDS}에 걸릴 때만 경조사로 보고, 그 외에는 무조건 {@code null}(선물)이다.
     * 일반 판정({@link #classify})을 선물명에 그대로 쓰면 "졸업 선물", "생일 축하" 같은 평범한 선물이
     * 전부 경조사로 넘어간다.
     */
    public static EventCategory classifyGiftName(String... giftNames) {
        String haystack = join(giftNames);
        if (haystack.isEmpty() || !containsAny(haystack, MONEY_WORDS)) {
            return null;
        }
        return classify(haystack);
    }

    private static String join(String... texts) {
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                sb.append(text).append(' ');
            }
        }
        return sb.toString();
    }

    private static boolean containsAny(String haystack, List<String> words) {
        return words.stream().anyMatch(haystack::contains);
    }
}
