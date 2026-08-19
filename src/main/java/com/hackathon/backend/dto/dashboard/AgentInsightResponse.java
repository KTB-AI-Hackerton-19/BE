package com.hackathon.backend.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 홈 화면 "마음 에이전트가 발견했어요" 카드. 다가오는 기념일/답례일 중 가장 가까운 하나를 골라 문구까지 만들어 내려준다. */
@Schema(description = "마음 에이전트 인사이트 카드 (해당 없으면 dashboard.agentInsight = null)")
public record AgentInsightResponse(
        @Schema(description = "BIRTHDAY(다가오는 생일) 또는 REMINDER(다가오는 답례일)",
                example = "BIRTHDAY", allowableValues = {"BIRTHDAY", "REMINDER"}) String type,
        @Schema(description = "대상 Person ID", example = "3") Long personId,
        @Schema(description = "대상 이름", example = "김민수") String person,
        @Schema(description = "해당 이벤트 날짜", example = "2026-09-18") LocalDate date,
        @Schema(description = "오늘부터 남은 일수", example = "31") int daysLeft,
        @Schema(description = "카드 제목 (그대로 출력)", example = "민수님의 생일이 한 달 남았어요") String title,
        @Schema(description = "카드 본문 (그대로 출력)",
                example = "지난 생일에 받은 케이크를 참고해, 부담 없이 마음을 전할 선물을 준비해볼까요?") String message,
        @Schema(description = "캘린더 위젯에 쓰는 영문 월 약어", example = "SEP") String monthLabel,
        @Schema(description = "캘린더 위젯에 쓰는 일자", example = "18") int dayLabel,
        @Schema(description = "캘린더 위젯 하단 캡션", example = "민수 생일") String caption
) {
}
