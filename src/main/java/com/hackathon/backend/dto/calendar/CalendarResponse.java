package com.hackathon.backend.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "월별 캘린더 응답")
public record CalendarResponse(
        @Schema(description = "조회한 연도", example = "2026") int year,
        @Schema(description = "조회한 월 (1~12)", example = "8") int month,
        @Schema(description = "이벤트가 있는 날짜만 담긴 목록 (날짜 오름차순). 이벤트 없는 날은 아예 포함되지 않는다")
        List<CalendarDayResponse> days
) {
}
