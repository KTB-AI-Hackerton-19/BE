package com.hackathon.backend.dto.gift;

import com.hackathon.backend.domain.Category;
import com.hackathon.backend.domain.EventCategory;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.Gender;
import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.domain.Person;
import com.hackathon.backend.domain.RecordType;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.support.MoneyFormatter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 프론트 디자인의 record 객체와 필드가 1:1로 대응되는 응답. 화면에서 추가 가공 없이 그대로 쓸 수 있게 구성했다. */
@Schema(description = "받은 마음(선물·부조금) 한 건")
public record GiftRecordResponse(
        @Schema(description = "기록 ID", example = "1") Long id,

        @Schema(description = "보낸 사람 Person ID. <b>null이면 '사람들'에 등록되지 않은 이름뿐인 기록</b>이다 "
                + "(경조사 하객 등). 이 경우 화면에서 '사람으로 등록' 버튼을 띄우고 "
                + "POST /api/gift-records/{id}/person 을 부르면 된다", example = "3") Long personId,
        @Schema(description = "보낸 사람 이름 — 화면의 person. 등록된 사람 → 사용자가 적은 이름 → AI 원본 순으로 채워진다",
                example = "김민수") String person,
        @Schema(description = "관계 — 화면의 relation", example = "친구") String relation,

        @Schema(description = "사람으로 등록하지 않은 보낸 사람 이름. personId가 null일 때 이 값이 곧 표시 이름이며, "
                + "PATCH로 수정할 수 있다", example = "김민수") String guestName,

        @Schema(description = "받은 날짜 — 화면의 date", example = "2026-08-18") LocalDate date,
        @Schema(description = "답례 알림일 — 화면의 reminderDate (미설정이면 null)", example = "2026-09-14") LocalDate reminderDate,
        @Schema(description = "받은 이유 (자유 텍스트) — 화면의 occasion. recordType=EVENT면 항상 null",
                example = "내 생일") String occasion,
        @Schema(description = "선물명 — 화면의 gift", example = "스타벅스 케이크") String gift,

        @Schema(description = "카테고리 ID. recordType=EVENT면 항상 null", example = "1") Long categoryId,
        @Schema(description = "카테고리 이름 — 화면의 category. recordType=EVENT면 항상 null", example = "디저트") String category,

        @Schema(description = "금액(원) 정수 — 정렬·집계·필터용", example = "35000") Integer amount,
        @Schema(description = "포맷된 금액 문자열 — 화면의 price. 그대로 출력하면 됨", example = "35,000원") String price,

        @Schema(description = "화면의 emoji — GIFT면 카테고리, EVENT면 경조사 유형에서 파생", example = "🍰") String emoji,
        @Schema(description = "화면의 color — GIFT면 카테고리, EVENT면 경조사 유형에서 파생", example = "mint") String color,

        @Schema(description = "감사/답례 완료 여부 — true면 '감사 완료', false면 '확인 필요' 뱃지", example = "true") boolean thanked,

        @Schema(description = "AI가 이미지에서 추출한 보낸 사람 이름 (확정 전 확인 폼 프리필용)", example = "김민수") String extractedSenderName,
        @Schema(description = "AI가 추측한 관계 (확정 전 확인 폼 프리필용)", example = "친구") String extractedRelationship,
        @Schema(description = "AI가 추정한 보낸 사람 나이 (새 사람 등록 폼 프리필용, 없으면 null)", example = "32") Integer extractedAge,
        @Schema(description = "AI가 추정한 보낸 사람 성별 (새 사람 등록 폼 프리필용, 없으면 null)", example = "남성") Gender extractedGender,
        @Schema(description = "원본 이미지 조회용 presigned GET URL (매 응답마다 새로 발급, 15분 만료)") String imageUrl,

        @Schema(description = "대분류 영문 코드. GIFT(선물) / EVENT(경조사)", example = "GIFT") RecordType recordType,
        @Schema(description = "대분류 한글 라벨. 화면에 그대로 출력하면 된다", example = "선물") String recordTypeLabel,
        @Schema(description = "경조사 여부(recordType=EVENT). 큰 틀 필터에 쓰면 된다", example = "false") boolean event,

        @Schema(description = "경조사 유형(고정 7종). recordType=EVENT일 때만 값이 있다", example = "WEDDING") EventCategory eventCategory,
        @Schema(description = "경조사 유형 한글 라벨", example = "결혼") String eventCategoryLabel,
        @Schema(description = "경조사 그룹 — CELEBRATION(경사) / CONDOLENCE(조사). recordType=EVENT일 때만 값이 있다")
        String eventGroup,
        @Schema(description = "경조사 그룹 한글 라벨", example = "경사") String eventGroupLabel,
        @Schema(description = "행사일. recordType=EVENT일 때만 값이 있다", example = "2026-05-10") LocalDate eventDate,

        @Schema(description = "DRAFT(AI 추출 직후, 사용자 확인 전) 또는 CONFIRMED(사용자 확정 완료)") GiftRecordStatus status,
        @Schema(description = "기록 생성 시각") LocalDateTime createdAt,

        @Schema(description = "true면 AI가 아니라 하드코딩 더미로 채워진 값이다. AI 서버가 죽어 있어도 화면은 정상으로 "
                + "보이기 때문에, 연동 확인 시 반드시 이 값을 봐야 한다", example = "false") boolean aiFallback,

        @Schema(description = "aiFallback이 true일 때 왜 실패했는지. AI가 준 에러 본문이 그대로 들어간다",
                example = "AI 502 BAD_GATEWAY: {\"detail\":\"이미지 분석에 실패했습니다.\"}") String aiError
) {
    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "blue";

    public static GiftRecordResponse from(GiftRecord record, String imageUrl) {
        return from(record, imageUrl, false, null);
    }

    public static GiftRecordResponse from(GiftRecord record, String imageUrl, boolean aiFallback, String aiError) {
        Person person = record.getPerson();
        Category category = record.getCategory();
        EventCategory eventCategory = record.getEventCategory();
        boolean isEvent = record.getRecordType() == RecordType.EVENT;

        String emoji = isEvent
                ? (eventCategory != null ? eventCategory.getEmoji() : DEFAULT_EMOJI)
                : (category != null ? category.getEmoji() : DEFAULT_EMOJI);
        String color = isEvent
                ? (eventCategory != null ? eventCategory.getColor() : DEFAULT_COLOR)
                : (category != null ? category.getColor() : DEFAULT_COLOR);

        return new GiftRecordResponse(
                record.getId(),
                person != null ? person.getId() : null,
                record.displayName(),
                Relationship.displayLabel(record.displayRelationship()),
                record.getGuestName(),
                record.getReceivedDate(),
                record.getReminderDate(),
                record.getOccasion(),
                record.getGiftName(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                record.getAmount(),
                MoneyFormatter.format(record.getAmount()),
                emoji,
                color,
                record.isThanked(),
                record.getExtractedSenderName(),
                Relationship.displayLabel(record.getExtractedRelationship()),
                record.getExtractedAge(),
                record.getExtractedGender(),
                imageUrl,
                record.getRecordType(),
                record.getRecordType() != null ? record.getRecordType().getLabel() : null,
                isEvent,
                eventCategory,
                eventCategory != null ? eventCategory.getLabel() : null,
                eventCategory != null ? eventCategory.getGroup().name() : null,
                eventCategory != null ? eventCategory.getGroup().getLabel() : null,
                record.getEventDate(),
                record.getStatus(),
                record.getCreatedAt(),
                aiFallback,
                aiError
        );
    }
}
