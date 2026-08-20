package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * AI가 만든 DRAFT를 사용자가 확인/수정해 확정하거나, 이미 저장된 기록을 수정할 때 쓰는 부분 수정 요청.
 * 보내지 않은(=null) 필드는 기존 값을 그대로 유지한다.
 */
@Schema(description = "마음 기록 수정/확정 요청 (부분 수정)")
public record GiftRecordUpdateRequest(
        @Schema(description = "보낸 사람 Person ID", example = "3") Long personId,
        @Schema(description = "보낸 사람 이름. personId 없이 이 이름만 보내면 <b>사람을 만들지 않고</b> 기록에만 남는다. "
                + "사람으로 등록하려면 registerPerson=true", example = "김민수") String personName,

        @Schema(description = "personName의 별칭(사람 미등록 이름). 둘 다 오면 이 값이 우선", example = "김민수")
        String guestName,

        @Schema(description = "true면 위 이름으로 Person을 만들어 연결한다. 생략하면 false", example = "false")
        Boolean registerPerson,
        @Schema(description = "관계 (GET /api/relationships 의 value)", example = "친구") String relation,
        @Schema(description = "카테고리 ID", example = "1") Long categoryId,
        @Schema(description = "카테고리 이름 (categoryId 대신 사용 가능)", example = "디저트") String category,
        @Schema(description = "받은 이유 (자유 텍스트)", example = "내 생일") String occasion,
        @Schema(description = "선물명", example = "스타벅스 케이크") String gift,
        @Schema(description = "금액. 숫자와 \"35,000원\" 형식 문자열 모두 허용", example = "35,000원") String price,
        @Schema(description = "받은 날짜", example = "2026-08-18") LocalDate date,
        @Schema(description = "답례 알림일. 값을 주면 답례 알림이 생성/재조정된다", example = "2026-09-14") LocalDate reminderDate,
        @Schema(description = "감사 완료 여부", example = "false") Boolean thanked,

        @Schema(description = "true면 이 요청으로 status를 CONFIRMED로 확정한다. 생략하면 true (모달 저장 = 확정)", example = "true")
        Boolean confirm
) {
}
