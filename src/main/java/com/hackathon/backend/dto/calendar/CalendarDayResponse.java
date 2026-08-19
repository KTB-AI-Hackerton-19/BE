package com.hackathon.backend.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "캘린더의 하루")
public record CalendarDayResponse(
        @Schema(description = "날짜", example = "2026-08-18") LocalDate date,
        @Schema(description = "받은 마음 개수 (칸에 이모지를 몇 개 그릴지 판단)", example = "1") int receivedCount,
        @Schema(description = "답례 알림 개수 (0보다 크면 빨간 dot 표시)", example = "0") int toGiveCount,
        @Schema(description = "그날의 이벤트 목록 (RECEIVED 먼저, TO_GIVE 나중)") List<CalendarEventResponse> events
) {
}
