package com.hackathon.backend.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 카테고리 부분 수정 요청. <b>모든 필드가 선택</b>이며, 보내지 않은 값은 기존 값이 유지된다.
 *
 * <p>생성용 {@link CategoryRequest}와 달리 이름에 필수 제약이 없다.
 * 이모지나 색만 바꾸는 경우가 대부분이라 이름까지 매번 실어 보내게 하면 불편하기 때문이다.</p>
 */
@Schema(description = "카테고리 부분 수정 요청. 보내지 않은 필드는 기존 값을 유지한다.")
public record CategoryUpdateRequest(
        @Schema(description = "바꿀 이름. 생략하면 그대로", example = "여행·체험") String name,

        @Schema(description = "바꿀 이모지. 생략하면 그대로", example = "✈️") String emoji,

        @Schema(description = "바꿀 카드 배경 테마 (mint / pink / blue / gold). 생략하면 그대로", example = "mint") String color,

        @Schema(description = "바꿀 정렬 순서. 생략하면 그대로", example = "80") Integer displayOrder,

        @Schema(description = "노출 여부. false면 목록에서 숨겨지며 기존 기록은 그대로 유지된다", example = "true") Boolean active
,

        @Schema(description = "탭 변경 — GIFT / CELEBRATION / CONDOLENCE (한글도 허용). 생략하면 그대로",
                example = "CELEBRATION")
        String kind,

        @Schema(description = "바꿀 행사일 (경조사 카테고리에서만 의미가 있다). 생략하면 그대로. "
                + "kind를 GIFT로 바꾸면 행사일은 자동으로 비워진다", example = "2026-05-10")
        LocalDate eventDate) {
}
