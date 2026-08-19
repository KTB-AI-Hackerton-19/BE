package com.hackathon.backend.dto.reminder;

import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.ReminderStatus;
import com.hackathon.backend.domain.ReminderTask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Schema(description = "답례 알림 한 건")
public record ReminderTaskResponse(
        @Schema(description = "알림 ID", example = "1") Long id,
        @Schema(description = "답례 대상 Person ID", example = "3") Long personId,
        @Schema(description = "답례 대상 이름", example = "김민수") String person,
        @Schema(description = "관계", example = "친한 친구") String relation,
        @Schema(description = "이 알림을 만든 기록 ID", example = "1") Long giftRecordId,
        @Schema(description = "받았던 선물명 (무엇에 대한 답례인지)", example = "스타벅스 케이크") String gift,
        @Schema(description = "알림 예정일", example = "2026-09-14") LocalDate scheduledAt,
        @Schema(description = "오늘부터 남은 일수 (음수면 이미 지난 알림)", example = "27") long daysLeft,
        @Schema(description = "PENDING(발송 대기) 또는 SENT(발송 완료)") ReminderStatus status,
        @Schema(description = "원본 기록의 감사 완료 여부", example = "false") boolean thanked
) {
    public static ReminderTaskResponse of(ReminderTask task, LocalDate today) {
        Person person = task.getPerson();
        GiftRecord record = task.getGiftRecord();
        return new ReminderTaskResponse(
                task.getId(),
                person != null ? person.getId() : null,
                person != null ? person.getName() : null,
                person != null ? person.getRelationship() : null,
                record != null ? record.getId() : null,
                record != null ? record.getGiftName() : null,
                task.getScheduledAt(),
                ChronoUnit.DAYS.between(today, task.getScheduledAt()),
                task.getStatus(),
                record != null && record.isThanked()
        );
    }
}
