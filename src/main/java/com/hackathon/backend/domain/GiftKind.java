package com.hackathon.backend.domain;

import java.util.List;

/**
 * 마음 기록의 큰 분류. "경조사냐 일반 선물이냐"를 한 값으로 결정한다.
 *
 * <p>경조사를 별도 컬럼(예: isEvent + eventType)으로 쪼개지 않은 이유는, 그러면
 * "일반 선물인데 조사 유형"처럼 앞뒤가 안 맞는 조합이 저장될 수 있기 때문이다.
 * 한 컬럼에 세 값만 두면 그런 상태가 아예 만들어지지 않는다.</p>
 *
 * <p>필터에서 "경조사 전체"는 {@link #EVENT_KINDS}(경사+조사)로 조회한다.</p>
 */
public enum GiftKind {

    /** 일반 선물 (생일, 집들이, 그냥 등) */
    GIFT("선물"),

    /** 경사 — 결혼, 출산, 돌, 승진 등 축하할 일 */
    CELEBRATION("경사"),

    /** 조사 — 장례 등 위로할 일 */
    CONDOLENCE("조사");

    /** "경조사"로 묶어 필터링할 때 쓰는 값들. */
    public static final List<GiftKind> EVENT_KINDS = List.of(CELEBRATION, CONDOLENCE);

    private final String label;

    GiftKind(String label) {
        this.label = label;
    }

    /** 화면에 그대로 쓸 한글 라벨. */
    public String getLabel() {
        return label;
    }

    /** 경조사인지 여부(경사 또는 조사). */
    public boolean isEvent() {
        return this != GIFT;
    }

    /**
     * 쿼리 파라미터를 값 목록으로 바꾼다.
     *
     * <ul>
     *   <li>{@code null} / 빈 값 / "전체" → 전부 (필터 없음)</li>
     *   <li>{@code EVENT} / "경조사" → 경사 + 조사</li>
     *   <li>{@code GIFT} / "선물", {@code CELEBRATION} / "경사", {@code CONDOLENCE} / "조사" → 그 하나</li>
     * </ul>
     *
     * <p>모르는 값이 오면 필터를 걸지 않는다(전체). 오타 하나로 목록이 텅 비는 것보다 낫다.</p>
     */
    public static List<GiftKind> parseFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(values());
        }
        String v = raw.trim().toUpperCase();
        return switch (v) {
            case "EVENT", "경조사" -> EVENT_KINDS;
            case "GIFT", "선물" -> List.of(GIFT);
            case "CELEBRATION", "경사" -> List.of(CELEBRATION);
            case "CONDOLENCE", "조사" -> List.of(CONDOLENCE);
            default -> List.of(values());
        };
    }

    /** 저장할 때 쓰는 파서. 한글 라벨도 받는다. 모르는 값이면 일반 선물로 본다. */
    public static GiftKind parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return GIFT;
        }
        return switch (raw.trim().toUpperCase()) {
            case "CELEBRATION", "경사" -> CELEBRATION;
            case "CONDOLENCE", "조사" -> CONDOLENCE;
            default -> GIFT;
        };
    }
}
