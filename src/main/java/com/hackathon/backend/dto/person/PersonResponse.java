package com.hackathon.backend.dto.person;

import com.hackathon.backend.domain.Gender;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.Relationship;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 사람들 화면의 목록 카드 한 장에 필요한 데이터. */
@Schema(description = "소중한 사람 한 명")
public record PersonResponse(
        @Schema(description = "사람 ID", example = "3") Long id,
        @Schema(description = "이름", example = "김민수") String name,
        @Schema(description = "관계 카테고리 (미지정이면 null)", example = "친구") Relationship relation,
        @Schema(description = "성별 (미입력이면 null)", example = "남성") Gender gender,
        @Schema(description = "생일", example = "1998-05-10") LocalDate birthday,
        @Schema(description = "메모 (취향/기피 품목 등)", example = "커피를 좋아함") String memo,
        @Schema(description = "이 사람에게 받은 마음 개수 — '마음 N개'", example = "2") long giftCount,
        @Schema(description = "가장 최근에 받은 선물명 — '최근 스타벅스 케이크'", example = "스타벅스 케이크") String latestGift,
        @Schema(description = "가장 최근에 받은 날짜", example = "2026-08-18") LocalDate latestReceivedDate,
        @Schema(description = "가장 가까운 답례 알림일 (없으면 null → 화면엔 '미정')", example = "2026-09-14") LocalDate upcomingReminderDate
) {
    public static PersonResponse of(Person person, long giftCount, GiftRecord latest, LocalDate upcomingReminderDate) {
        return new PersonResponse(
                person.getId(),
                person.getName(),
                person.getRelationship(),
                person.getGender(),
                person.getBirthday(),
                person.getMemo(),
                giftCount,
                latest != null ? latest.getGiftName() : null,
                latest != null ? latest.getReceivedDate() : null,
                upcomingReminderDate
        );
    }
}
