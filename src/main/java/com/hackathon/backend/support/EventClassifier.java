package com.hackathon.backend.support;

import com.hackathon.backend.domain.GiftKind;
import java.util.List;

/**
 * AI가 준 텍스트(경조사명·받은 이유·선물명)를 보고 <b>경조사인지, 경사인지 조사인지</b>를 판정한다.
 *
 * <p>AI에게 GiftKind enum을 직접 뱉으라고 요구하지 않는 이유는, 프롬프트가 바뀌거나 모델이 바뀌면
 * "CELEBRATION" 대신 "결혼식"이 오는 식으로 쉽게 어긋나기 때문이다. 자유 텍스트를 받아
 * 백엔드에서 한 번 정규화하면 AI 쪽 출력이 흔들려도 탭 분류는 유지된다.</p>
 */
public final class EventClassifier {

    /** 조사(위로할 일). 경사보다 먼저 본다 — 조문을 축하로 잘못 분류하는 쪽이 훨씬 치명적이다. */
    private static final List<String> CONDOLENCE_WORDS = List.of(
            "장례", "조의", "부의", "빈소", "발인", "상가", "문상", "조문", "근조", "삼우", "추도", "추모",
            "별세", "부고", "상주", "위로");

    /**
     * 경사(축하할 일).
     *
     * <p>"축하", "생일" 같은 일반적인 축하 표현은 일부러 뺐다. 그런 말은 평범한 생일 선물에도 붙어 있어서,
     * 넣어두면 케이크 하나 받은 기록이 경조사 탭으로 넘어간다.</p>
     */
    private static final List<String> CELEBRATION_WORDS = List.of(
            "결혼", "웨딩", "혼례", "화촉", "신혼", "축의", "돌잔치", "첫돌", "백일", "출산",
            "회갑", "환갑", "칠순", "팔순", "고희", "승진", "영전", "취임", "개업", "개원", "창업",
            "입학", "졸업", "합격", "취업");

    /** 경조사인 건 분명한데 경사/조사가 안 갈리는 말들. 이것만 걸리면 경사로 본다(아래 주석 참고). */
    private static final List<String> AMBIGUOUS_WORDS = List.of("경조", "부조", "봉투", "방명록");

    /**
     * 선물명만 보고도 경조사가 확실한 말들. 일반 선물 이름으로는 절대 쓰이지 않는다.
     * ("생일 축하 케이크"가 경조사로 넘어가는 걸 막으려고 선물명은 원래 판정에서 뺐는데,
     *  축의금·부의금처럼 그 자체가 경조사인 것까지 놓치면 반대로 전부 '기타 선물'이 된다.)
     */
    private static final List<String> MONEY_WORDS = List.of(
            "축의금", "축의", "부의금", "부의", "조의금", "조의", "부조금", "부조", "조위금", "근조", "화환");

    private static final String CELEBRATION_FALLBACK_NAME = "경사";
    private static final String CONDOLENCE_FALLBACK_NAME = "조사";

    /** 카테고리 이름 컬럼 길이(50)에 맞춘 상한. */
    private static final int MAX_NAME_LENGTH = 50;

    private EventClassifier() {
    }

    /**
     * 넘긴 텍스트 중 하나라도 경조사 단어에 걸리면 그 분류를, 아니면 {@link GiftKind#GIFT}를 돌려준다.
     *
     * <p>"부조금"처럼 경사·조사가 안 갈리는 단어만 걸린 경우는 경사로 둔다. 어차피 확정 전 DRAFT라
     * 사용자가 확인 폼에서 바꿀 수 있고, 최소한 "선물 탭"이 아니라 경조사 탭에는 올라가기 때문이다.</p>
     */
    public static GiftKind classify(String... texts) {
        String haystack = join(texts);
        if (haystack.isEmpty()) {
            return GiftKind.GIFT;
        }
        if (containsAny(haystack, CONDOLENCE_WORDS)) {
            return GiftKind.CONDOLENCE;
        }
        if (containsAny(haystack, CELEBRATION_WORDS) || containsAny(haystack, AMBIGUOUS_WORDS)) {
            return GiftKind.CELEBRATION;
        }
        return GiftKind.GIFT;
    }

    /**
     * 경조사 카테고리(=경조사 탭의 이벤트) 이름을 정한다. AI가 준 경조사명을 그대로 쓰고,
     * 없으면 분류 라벨("경사"/"조사")로 대신한다.
     */
    public static String eventName(GiftKind kind, String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                String name = candidate.trim();
                return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            }
        }
        return kind == GiftKind.CONDOLENCE ? CONDOLENCE_FALLBACK_NAME : CELEBRATION_FALLBACK_NAME;
    }

    /**
     * 선물명 전용 판정. {@link #MONEY_WORDS}에 걸릴 때만 경조사로 보고, 그 외에는 무조건
     * {@link GiftKind#GIFT}다. 일반 판정({@link #classify})을 선물명에 그대로 쓰면
     * "졸업 선물", "생일 축하" 같은 평범한 선물이 전부 경조사 탭으로 넘어간다.
     */
    public static GiftKind classifyGiftName(String... giftNames) {
        String haystack = join(giftNames);
        if (haystack.isEmpty() || !containsAny(haystack, MONEY_WORDS)) {
            return GiftKind.GIFT;
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
