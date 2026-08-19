package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 기록 모달 "확인/수정" 폼의 저장 요청 (사진 없이 직접 등록할 때도 동일하게 사용).
 *
 * <p>보낸 사람은 personId로 지정해도 되고, 폼처럼 이름을 그대로 보내도 된다(personName).
 * personId가 없으면 이름으로 기존 사람을 찾고, 없으면 새로 만든다.</p>
 */
@Schema(description = "마음 기록 등록 요청")
public record GiftRecordCreateRequest(
        @Schema(description = "보낸 사람 Person ID. 이미 등록된 사람을 고른 경우에만 사용", example = "3") Long personId,

        @Schema(description = "보낸 사람 이름 (모달의 '보낸 사람' 입력값). personId가 없으면 이 이름으로 찾거나 새로 등록한다", example = "김민수")
        String personName,

        @Schema(description = "관계 (모달의 '관계' 입력값). 사람을 새로 만들 때 함께 저장되고, 기존 사람이면 값을 갱신한다", example = "친한 친구")
        String relation,

        @Schema(description = "카테고리 ID. GET /api/categories 로 받은 id", example = "1") Long categoryId,

        @Schema(description = "카테고리 이름. categoryId 대신 이름으로 보내도 된다(없으면 '기타' 처리)", example = "디저트")
        String category,

        @Schema(description = "받은 이유 (자유 텍스트)", example = "내 생일") String occasion,

        @Schema(description = "선물명", example = "스타벅스 케이크") String gift,

        @Schema(description = "금액. 숫자(35000)와 문자열(\"35,000원\") 모두 허용 — 서버가 숫자만 뽑아 정수로 저장한다", example = "35,000원")
        String price,

        @Schema(description = "받은 날짜", example = "2026-08-18")
        @NotNull(message = "받은 날짜를 입력해주세요.") LocalDate date,

        @Schema(description = "답례 알림일. 넣으면 답례 알림(ReminderTask)이 함께 생성된다", example = "2026-09-14")
        LocalDate reminderDate,

        @Schema(description = "감사 완료 여부. 생략하면 false('확인 필요')", example = "false") Boolean thanked
) {
}
