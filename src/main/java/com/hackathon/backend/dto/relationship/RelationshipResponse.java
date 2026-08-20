package com.hackathon.backend.dto.relationship;

import com.hackathon.backend.domain.CustomRelationship;
import com.hackathon.backend.domain.Relationship;
import io.swagger.v3.oas.annotations.media.Schema;

/** 관계 드롭다운 옵션 한 개. value를 그대로 요청에 실어 보내면 된다. */
@Schema(description = "관계 카테고리 옵션")
public record RelationshipResponse(
        @Schema(description = "내가 추가한 관계의 ID. 기본 9종은 null", example = "12") Long id,
        @Schema(description = "요청에 그대로 넣는 값(= 한글 라벨)", example = "친구") String value,
        @Schema(description = "화면에 표시할 라벨", example = "친구") String label,
        @Schema(description = "기본 관계면 enum 이름(FRIEND), 내가 추가한 관계면 \"CUSTOM\". "
                + "프론트에서 아이콘 매핑 등에 쓰고 싶을 때만 사용", example = "FRIEND") String code,
        @Schema(description = "내가 추가한 관계인지 여부. true인 항목만 삭제/구분 표시하면 된다", example = "false")
        boolean custom
) {
    /** 커스텀 항목의 code. 프론트가 아이콘 매핑에서 기본값으로 떨어뜨릴 수 있게 null 대신 고정 문자열을 쓴다. */
    public static final String CUSTOM_CODE = "CUSTOM";

    public static RelationshipResponse of(Relationship relationship) {
        return new RelationshipResponse(null, relationship.getLabel(), relationship.getLabel(),
                relationship.name(), false);
    }

    public static RelationshipResponse of(CustomRelationship relationship) {
        return new RelationshipResponse(relationship.getId(), relationship.getName(), relationship.getName(),
                CUSTOM_CODE, true);
    }
}
