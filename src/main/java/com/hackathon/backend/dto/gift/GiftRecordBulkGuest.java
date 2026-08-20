package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 벌크 저장의 <b>하객 한 명</b> (선물 벌크에서도 같은 모양을 쓴다).
 *
 * <p>단건 등록({@link GiftRecordCreateRequest})과 필드가 같지만 <b>전부 선택값</b>이다.
 * 비워 둔 값은 {@link GiftRecordBulkCreateRequest}의 공통값을 그대로 물려받는다 —
 * 하객 명단은 이름과 금액만 다르고 행사·날짜는 전원이 같기 때문이다.</p>
 */
@Schema(description = "guests 배열의 원소 하나. 비운 필드는 요청 최상위의 공통값을 물려받는다")
public record GiftRecordBulkGuest(
        @Schema(description = "보낸 사람 Person ID. 이미 등록된 사람을 고른 경우에만", example = "3") Long personId,

        @Schema(description = "보낸 사람 이름. personId 없이 이름만 보내면 사람을 만들지 않고 기록에만 남는다", example = "김민수")
        String personName,

        @Schema(description = "personName의 별칭. 둘 다 오면 이 값이 우선", example = "김민수") String guestName,

        @Schema(description = "true면 이 사람을 '사람들' 목록에도 등록한다. 하객 명단에는 보통 쓰지 않는다", example = "false")
        Boolean registerPerson,

        @Schema(description = "관계", example = "친구") String relation,

        @Schema(description = "대분류. 생략하면 공통값", example = "EVENT") String recordType,

        @Schema(description = "카테고리 ID (선물일 때만). 생략하면 공통값", example = "1") Long categoryId,

        @Schema(description = "카테고리 이름 (선물일 때만). 생략하면 공통값", example = "디저트") String category,

        @Schema(description = "경조사 유형 (경조사일 때만). 생략하면 공통값", example = "결혼") String eventCategory,

        @Schema(description = "행사일. 생략하면 공통값", example = "2026-05-10") LocalDate eventDate,

        @Schema(description = "받은 이유 (선물일 때만). 생략하면 공통값", example = "내 생일") String occasion,

        @Schema(description = "선물명. 선물(GIFT) 기록에서 필수", example = "스타벅스 케이크") String gift,

        @Schema(description = "금액 — 필수. 숫자(50000)와 문자열(\"50,000원\") 모두 허용", example = "50,000원") String price,

        @Schema(description = "받은 날짜. 생략하면 공통값", example = "2026-08-18") LocalDate date,

        @Schema(description = "답례 알림일. 생략하면 공통값", example = "2026-09-14") LocalDate reminderDate,

        @Schema(description = "감사 완료 여부. 생략하면 공통값(없으면 false)", example = "false") Boolean thanked
) {
}
