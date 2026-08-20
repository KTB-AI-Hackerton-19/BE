package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사람 연결 결과")
public record GiftRecordPersonLinkResponse(

        @Schema(description = "연결된 Person ID", example = "3") Long personId,
        @Schema(description = "연결된 사람 이름", example = "김민수") String personName,

        @Schema(description = "이번 요청으로 Person이 새로 만들어졌으면 true, 기존 사람에 붙였으면 false", example = "true")
        boolean personCreated,

        @Schema(description = "이 사람으로 연결된 기록 수. applySameName=true면 2 이상일 수 있다", example = "3")
        int linkedCount,

        @Schema(description = "연결된 기록 전체(갱신된 상태)")
        List<GiftRecordResponse> records
) {
}
