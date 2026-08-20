package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/**
 * 마음 기록의 대분류. "선물이냐 경조사냐"를 결정한다.
 *
 * <p>GIFT는 {@link Category}(사용자별 자유 카테고리)를, EVENT는 {@link EventCategory}(고정 7종)를 쓴다.
 * 한 기록이 둘을 동시에 갖지 않도록 {@link GiftRecord}가 저장 시점에 강제한다.</p>
 */
@Schema(description = "마음 기록 대분류 (영문 코드). 한글 라벨은 응답의 recordTypeLabel에 따로 내려간다",
        allowableValues = {"GIFT", "EVENT"}, example = "GIFT")
public enum RecordType {

    GIFT("선물"),
    EVENT("경조사");

    private final String label;

    RecordType(String label) {
        this.label = label;
    }

    /** 한글 라벨. JSON에는 GIFT/EVENT(enum 이름)가 나가고, 이 라벨은 recordTypeLabel 같은 별도 필드로만 내려간다. */
    public String getLabel() {
        return label;
    }

    /** 화면이 보내는 한글 라벨과 enum 이름 둘 다 받는다. 모르는 값이면 GIFT로 본다(기본값이 선물이라 가장 안전). */
    @JsonCreator
    public static RecordType parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return GIFT;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(t -> t.label.equals(trimmed) || t.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(GIFT);
    }
}
