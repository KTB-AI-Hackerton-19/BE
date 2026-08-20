package com.hackathon.backend.dto.gift;

import com.hackathon.backend.domain.Relationship;
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

        @Schema(description = "관계 카테고리 (모달의 '관계' 드롭다운 값). 사람을 새로 만들 때 함께 저장되고, 기존 사람이면 값을 갱신한다. "
                + "선택지는 GET /api/relationships 참고", example = "친구")
        Relationship relation,

        @Schema(description = "대분류. GIFT(선물, 기본) / EVENT(경조사). 한글(선물/경조사)도 허용", example = "GIFT")
        String recordType,

        @Schema(description = "카테고리 ID. GET /api/categories 로 받은 id. recordType=GIFT일 때만 쓰인다", example = "1") Long categoryId,

        @Schema(description = "카테고리 이름. categoryId 대신 이름으로 보내도 된다(없으면 '기타' 처리). recordType=GIFT일 때만 쓰인다",
                example = "디저트")
        String category,

        @Schema(description = "경조사 유형. recordType=EVENT일 때 필수 — 결혼/출산·돌잔치/수연/취업·승진/개업·이사/장례식/제사·탈상 "
                + "(영문 코드 WEDDING 등도 허용) 중 하나만 가능하다. GET /api/gift-records/event-categories 참고", example = "결혼")
        String eventCategory,

        @Schema(description = "행사일. recordType=EVENT일 때만 쓰인다 — 결혼식·장례식이 실제로 열린 날", example = "2026-05-10")
        LocalDate eventDate,

        @Schema(description = "받은 이유 (자유 텍스트). recordType=GIFT일 때만 쓰인다", example = "내 생일") String occasion,

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
