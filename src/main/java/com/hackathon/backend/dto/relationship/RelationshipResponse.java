package com.hackathon.backend.dto.relationship;

import com.hackathon.backend.domain.Relationship;
import io.swagger.v3.oas.annotations.media.Schema;

/** 관계 드롭다운 옵션 한 개. value를 그대로 요청에 실어 보내면 된다. */
@Schema(description = "관계 카테고리 옵션")
public record RelationshipResponse(
        @Schema(description = "요청에 그대로 넣는 값(= 한글 라벨)", example = "친구") String value,
        @Schema(description = "화면에 표시할 라벨", example = "친구") String label,
        @Schema(description = "enum 이름. 프론트에서 아이콘 매핑 등에 쓰고 싶을 때만 사용", example = "FRIEND") String code
) {
    public static RelationshipResponse of(Relationship relationship) {
        return new RelationshipResponse(relationship.getLabel(), relationship.getLabel(), relationship.name());
    }
}
