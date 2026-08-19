package com.hackathon.backend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

/** 홈 화면 상단 통계 카드 3개. value/detail 문자열을 서버가 완성해서 내려주므로 프론트는 그대로 출력하면 된다. */
@Schema(description = "홈 상단 통계 3종")
public record DashboardStatsResponse(
        @Schema(description = "기록한 마음 총 개수", example = "4") long totalRecords,
        @Schema(description = "'기록한 마음' 카드 값", example = "4개") String totalRecordsText,
        @Schema(description = "이번 달에 새로 기록한 개수", example = "3") long recordsThisMonth,
        @Schema(description = "'기록한 마음' 카드 보조 문구", example = "이번 달 +3") String recordsThisMonthText,

        @Schema(description = "소중한 사람 수", example = "3") long totalPeople,
        @Schema(description = "'소중한 사람' 카드 값", example = "3명") String totalPeopleText,

        @Schema(description = "오늘 이후로 남아있는 답례 알림 개수", example = "2") long upcomingReminders,
        @Schema(description = "'다가오는 일정' 카드 값", example = "2개") String upcomingRemindersText,
        @Schema(description = "가장 가까운 일정까지 남은 일수 (없으면 null)", example = "27") Integer daysToNearestReminder,
        @Schema(description = "'다가오는 일정' 카드 보조 문구", example = "가장 가까운 일정 27일 후") String nearestReminderText
) {
}
