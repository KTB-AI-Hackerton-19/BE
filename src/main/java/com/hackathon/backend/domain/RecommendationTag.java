package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/** 선물 추천 카드 우측 상단에 붙는 뱃지. 디자인의 고정 3종과 1:1 대응. */
@Schema(description = "추천 뱃지", allowableValues = {"취향 일치", "실패 확률 낮음", "답례 추천"})
public enum RecommendationTag {

    TASTE_MATCH("취향 일치"),
    SAFE_CHOICE("실패 확률 낮음"),
    THANK_YOU("답례 추천");

    private final String label;

    RecommendationTag(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RecommendationTag from(String value) {
        if (value == null || value.isBlank()) {
            return SAFE_CHOICE;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(t -> t.label.equals(trimmed) || t.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(SAFE_CHOICE);
    }
}
