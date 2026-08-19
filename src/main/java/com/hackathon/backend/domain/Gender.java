package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;

/** 사람의 성별. 선물 추천의 참고 정보로 쓰인다. 입력하지 않아도 되는 선택 항목이라 null을 허용한다. */
@Schema(description = "성별", allowableValues = {"남성", "여성", "기타"})
public enum Gender {

    MALE("남성"),
    FEMALE("여성"),
    OTHER("기타");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /** 화면이 보내는 한글 라벨("여성")과 enum 이름("FEMALE") 둘 다 받는다. 모르는 값이면 null(미입력)로 둔다. */
    @JsonCreator
    public static Gender from(String value) {
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
