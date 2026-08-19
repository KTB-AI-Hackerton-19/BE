package com.hackathon.backend.dto.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 캘린더 한 칸(또는 날짜 상세)에 표시되는 이벤트 한 건.
 * 날짜 상세 패널("이날의 마음")까지 이 데이터만으로 그릴 수 있도록 선물명·금액·이유를 모두 담았다.
 */
@Schema(description = "캘린더 이벤트")
public record CalendarEventResponse(
        @Schema(description = "RECEIVED면 GiftRecord ID, TO_GIVE면 ReminderTask ID", example = "1") Long id,

        @Schema(description = "RECEIVED(받은 마음, 초록/이모지 표시) 또는 TO_GIVE(답례 알림, 빨간 dot 표시)",
                example = "RECEIVED", allowableValues = {"RECEIVED", "TO_GIVE"}) String type,

        @Schema(description = "이벤트 날짜", example = "2026-08-18") LocalDate date,
        @Schema(description = "관련 기록 ID (TO_GIVE도 원본 기록으로 이동할 수 있게 제공)", example = "1") Long giftRecordId,
        @Schema(description = "상대방 Person ID", example = "3") Long personId,
        @Schema(description = "상대방 이름", example = "김민수") String person,
        @Schema(description = "선물명", example = "스타벅스 케이크") String gift,
        @Schema(description = "받은 이유", example = "내 생일") String occasion,
        @Schema(description = "카테고리 이름", example = "디저트") String category,
        @Schema(description = "표시용 이모지. TO_GIVE는 항상 🔔", example = "🍰") String emoji,
        @Schema(description = "카드 배경 테마", example = "mint") String color,
        @Schema(description = "금액(원)", example = "35000") Integer amount,
        @Schema(description = "포맷된 금액 문자열", example = "35,000원") String price,
        @Schema(description = "감사 완료 여부 (RECEIVED만 의미 있음)", example = "true") boolean thanked
) {
}
