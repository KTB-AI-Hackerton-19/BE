package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 선물 추천 요청 바디 ({@code POST {AI_SERVICE_URL}/api/v1/agent/recommend}).
 *
 * <p>AI 명세의 {@code RecommendRequest}와 1:1. 모든 필드가 선택이라 <b>모르는 값은 아예 보내지 않는다</b>
 * ({@code JsonInclude.NON_NULL}). null을 보내는 것과 안 보내는 것은 서버 기본값 적용 여부가 달라질 수 있어
 * 생략하는 쪽이 안전하다.</p>
 *
 * <pre>
 * {
 *   "age": 29, "gender": "male",
 *   "budget_min": 28000, "budget_max": 42000,
 *   "categories": ["디저트"],
 *   "gift_name": "스타벅스 케이크", "gift_price": 35000,
 *   "person_name": "김민수", "relationship": "친한 친구"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiRecommendRequest(
        @JsonProperty("age") Integer age,

        /** male / female / unknown 세 값만 허용된다. 모르면 보내지 않는다(서버 기본값 unknown). */
        @JsonProperty("gender") String gender,

        @JsonProperty("budget_min") Integer budgetMin,
        @JsonProperty("budget_max") Integer budgetMax,

        /** 지정하면 이 안에서만 추천한다. 최대 3개. */
        @JsonProperty("categories") List<String> categories,

        @JsonProperty("gift_name") String giftName,
        @JsonProperty("gift_price") Integer giftPrice,
        @JsonProperty("person_name") String personName,
        @JsonProperty("relationship") String relationship,

        /** 받은 이유(생일·집들이 등). 우리 기록의 occasion을 그대로 넘긴다. */
        @JsonProperty("event") String event,

        /** 취향. 최대 5개. Person.memo 중 기피 표현이 아닌 것만 넘긴다. */
        @JsonProperty("interests") List<String> interests,

        /**
         * 기피 품목. 최대 5개. Person.memo에 섞여 들어오는 "견과류 알레르기" 같은 항목을 여기로 보낸다.
         *
         * <p>{@link #interests}와 갈라야 하는 이유가 분명하다. 한 필드로 뭉쳐 보내면 알레르기 품목이
         * <b>관심사로 뒤집혀</b> 전달돼 AI가 그걸 추천 근거로 삼는다.</p>
         */
        @JsonProperty("dislikes") List<String> dislikes
) {
}
