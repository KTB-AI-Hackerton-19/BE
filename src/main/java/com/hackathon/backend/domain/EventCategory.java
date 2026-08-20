package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/**
 * 경조사 유형. 선물 카테고리({@link Category})와 달리 <b>고정 7종</b>이다 — 결혼식이 갑자기 새 항목으로
 * 늘어날 일이 없어, 사용자가 자유롭게 만들던 이전 방식(경조사 카테고리 row) 대신 코드로 고정했다.
 *
 * <p>{@code emoji}/{@code color}를 값마다 들고 있는 이유: 선물은 {@link Category} row에서 이 값을 가져오지만,
 * 경조사는 더 이상 row가 없어 어디서도 파생할 곳이 없기 때문이다.</p>
 */
@Schema(description = "경조사 유형", allowableValues = {
        "결혼", "출산/돌잔치", "수연", "취업/승진", "개업/이사", "장례식", "제사/탈상"})
public enum EventCategory {

    WEDDING("결혼", EventGroup.CELEBRATION, "💍", "gold"),
    CHILDBIRTH("출산/돌잔치", EventGroup.CELEBRATION, "👶", "pink"),
    LONGEVITY_BIRTHDAY("수연", EventGroup.CELEBRATION, "🎂", "gold"),
    EMPLOYMENT_PROMOTION("취업/승진", EventGroup.CELEBRATION, "💼", "mint"),
    OPENING_MOVING("개업/이사", EventGroup.CELEBRATION, "🏠", "mint"),
    FUNERAL("장례식", EventGroup.CONDOLENCE, "🕊️", "blue"),
    MEMORIAL_SERVICE("제사/탈상", EventGroup.CONDOLENCE, "🕯️", "blue");

    private final String label;
    private final EventGroup group;
    private final String emoji;
    private final String color;

    EventCategory(String label, EventGroup group, String emoji, String color) {
        this.label = label;
        this.group = group;
        this.emoji = emoji;
        this.color = color;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    public EventGroup getGroup() {
        return group;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getColor() {
        return color;
    }

    /**
     * 화면/요청이 보내는 한글 라벨("결혼")과 enum 이름("WEDDING") 둘 다 받는다.
     * 매칭되지 않으면 {@code null} — 경조사는 고정 7종만 허용하므로, 여기서는 조용히 폴백하지 않고
     * 호출부(서비스 계층)가 유효성 검증 실패로 처리해야 한다.
     */
    @JsonCreator
    public static EventCategory from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(c -> c.label.equals(trimmed) || c.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }
}
