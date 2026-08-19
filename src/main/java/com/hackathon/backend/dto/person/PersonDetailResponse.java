package com.hackathon.backend.dto.person;

import com.hackathon.backend.dto.gift.GiftRecordResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 사람 상세 화면 한 번에 그리기 위한 응답 (요약 + 주고받은 마음 타임라인). */
@Schema(description = "사람 상세 — 요약 정보와 타임라인을 한 번에 내려준다")
public record PersonDetailResponse(
        @Schema(description = "사람 요약 (이름/관계/마음 개수/최근 받은 날/다가오는 알림일)") PersonResponse person,
        @Schema(description = "주고받은 마음 타임라인 (받은 날짜 최신순)") List<GiftRecordResponse> records
) {
}
