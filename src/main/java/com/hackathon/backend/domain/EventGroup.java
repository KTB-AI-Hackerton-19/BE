package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/** {@link EventCategory} 7종을 "경사냐 조사냐"로 묶은 큰 분류. 필터·정렬에 쓴다. */
@Schema(description = "경조사 대분류", allowableValues = {"경사", "조사"})
public enum EventGroup {

    CELEBRATION("경사"),
    CONDOLENCE("조사");

    private final String label;

    EventGroup(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static EventGroup from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(g -> g.label.equals(trimmed) || g.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }
}
