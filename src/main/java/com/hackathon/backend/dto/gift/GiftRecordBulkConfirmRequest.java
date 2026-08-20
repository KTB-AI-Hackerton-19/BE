package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * AI가 만든 DRAFT 여러 건을 <b>한 요청으로</b> 확정한다 ({@code PATCH /api/gift-records/bulk}).
 *
 * <p>사진 한 장에서 여러 명이 나오면 사람 수만큼 DRAFT가 생기는데, 확인 폼에서 사용자가 고치는 값
 * (행사 유형·행사일·답례 알림일)은 <b>전원 공통</b>이다. 사람마다 다른 값(이름·금액·받은 날짜)은
 * AI가 넣어 둔 것을 그대로 두므로, 여기서는 <b>공통값만</b> 받는다 —
 * 값을 안 보낸 필드는 각 기록의 기존 값이 유지된다.</p>
 *
 * <p>{@link GiftRecordBulkCreateRequest}와 마찬가지로 <b>전부 확정되거나 전부 확정되지 않는다.</b>
 * 어느 기록의 어느 칸이 문제인지는 {@code error.fields}에 {@code ids[2].eventCategory} 형태로 담긴다
 * (인덱스는 보낸 {@code ids} 순서다).</p>
 */
@Schema(description = "DRAFT 여러 건을 한 번에 확정. 공통으로 고친 값만 보내고, 사람별 값은 기존 값이 유지된다")
public record GiftRecordBulkConfirmRequest(
        @Schema(description = "확정할 기록 ID 목록. POST /api/gift-records/extract 응답의 records[].id", example = "[226, 327]")
        @NotEmpty(message = "확정할 기록을 한 건 이상 보내주세요.")
        @Size(max = 200, message = "한 번에 확정할 수 있는 기록은 200건까지입니다.")
        List<Long> ids,

        @Schema(description = "대분류. GIFT(선물) / EVENT(경조사). 생략하면 각 기록의 기존 값 유지", example = "EVENT")
        String recordType,

        @Schema(description = "경조사 유형 (고정 7종). recordType=EVENT일 때 확정에 필요하다", example = "결혼")
        String eventCategory,

        @Schema(description = "행사일", example = "2026-08-15") LocalDate eventDate,

        @Schema(description = "카테고리 ID (선물일 때만)", example = "1") Long categoryId,

        @Schema(description = "카테고리 이름 (선물일 때만)", example = "디저트") String category,

        @Schema(description = "받은 이유 (선물일 때만)", example = "내 생일") String occasion,

        @Schema(description = "선물명", example = "축의금") String gift,

        @Schema(description = "답례 알림일. 같은 행사·같은 답례일이면 구글 캘린더 일정 하나로 묶인다", example = "2026-09-14")
        LocalDate reminderDate,

        @Schema(description = "감사 완료 여부", example = "false") Boolean thanked,

        @Schema(description = "false를 주면 DRAFT 상태를 유지한 채 값만 고친다. 생략하면 true(확정)", example = "true")
        Boolean confirm
) {

    /** 공통값을 단건 수정 요청으로 옮긴다. 사람별 값(personId/이름/금액/받은 날짜)은 건드리지 않는다. */
    public GiftRecordUpdateRequest toUpdateRequest() {
        return new GiftRecordUpdateRequest(
                null, null, null, null, null,
                recordType, categoryId, category, eventCategory, eventDate,
                occasion, gift, null, null, reminderDate, thanked, confirm);
    }
}
