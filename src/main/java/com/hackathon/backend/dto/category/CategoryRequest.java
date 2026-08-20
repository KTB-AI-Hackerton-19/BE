package com.hackathon.backend.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카테고리 생성/수정 요청. 이 API로 카테고리를 추가하면 코드 수정·재배포 없이 바로 필터 칩과 모달 select에 반영된다. "
        + "선물 카테고리 전용이다(경조사는 고정 7종이라 여기로 추가하지 않는다).")
public record CategoryRequest(
        @Schema(description = "카테고리 이름 (화면에 그대로 노출, 중복 불가)", example = "여행·체험")
        @NotBlank(message = "카테고리 이름을 입력해주세요.") String name,

        @Schema(description = "기본 이모지. 생략하면 🎁", example = "✈️") String emoji,

        @Schema(description = "카드 배경 테마. mint / pink / blue / gold 중 하나. 생략하면 blue", example = "mint") String color,

        @Schema(description = "정렬 순서 (작을수록 앞). 생략하면 맨 뒤", example = "80") Integer displayOrder,

        @Schema(description = "노출 여부. 생략하면 true", example = "true") Boolean active) {
}
