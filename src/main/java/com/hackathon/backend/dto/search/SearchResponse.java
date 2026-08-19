package com.hackathon.backend.dto.search;

import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.person.PersonResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 상단바 통합 검색("사람이나 선물을 검색해보세요") 결과. */
@Schema(description = "통합 검색 결과")
public record SearchResponse(
        @Schema(description = "검색어", example = "민수") String query,
        @Schema(description = "이름이 일치하는 사람 목록") List<PersonResponse> people,
        @Schema(description = "선물명/받은 이유/보낸 사람 이름이 일치하는 마음 기록 목록") List<GiftRecordResponse> records
) {
}
