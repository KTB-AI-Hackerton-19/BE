package com.hackathon.backend.dto.relationship;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관계 추가 요청")
public record RelationshipRequest(
        @Schema(description = "추가할 관계 이름. 이 값이 그대로 드롭다운에 뜨고, 사람·기록에도 그대로 저장된다",
                example = "동호회")
        @NotBlank(message = "관계 이름을 입력해주세요.")
        @Size(max = 30, message = "관계 이름은 30자를 넘을 수 없습니다.")
        String name
) {
}
