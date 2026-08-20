package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 여러 건을 <b>한 요청으로</b> 저장한다 ({@code POST /api/gift-records/bulk}).
 *
 * <p>주 용도는 <b>경조사 하객 명단</b>이다. 결혼식 하객 50명을 기록하려고 단건 등록을 50번 부르면
 * 요청이 50개 날아가고 중간에 하나만 실패해도 어디까지 저장됐는지 알 수 없다.
 * 행사 정보(유형·행사일·받은 날짜)는 전원이 같으므로 <b>공통값을 최상위에 한 번</b> 적고,
 * {@link #guests}에는 사람마다 다른 값(이름·금액)만 채우면 된다.</p>
 *
 * <p><b>전부 저장되거나 전부 저장되지 않는다.</b> 한 줄이라도 문제가 있으면 아무것도 저장하지 않고,
 * 몇 번째 줄의 어느 칸이 문제인지 {@code error.fields}에 {@code guests[2].price} 형태로 모아서 알려준다.
 * 부분 저장은 "50명 중 몇 명이 들어갔지?"를 사용자가 손으로 세게 만들기 때문에 택하지 않았다.</p>
 */
@Schema(description = "마음 기록 여러 건을 한 번에 저장. 공통값(행사·날짜)은 최상위에, 사람마다 다른 값만 guests에 적는다")
public record GiftRecordBulkCreateRequest(
        @Schema(description = "대분류 공통값. GIFT(선물) / EVENT(경조사). 한글(선물/경조사)도 허용. "
                + "생략하면 EVENT — 이 API는 하객 명단이 주 용도다", example = "EVENT")
        String recordType,

        @Schema(description = "경조사 유형 공통값. recordType=EVENT일 때 필수 — "
                + "결혼/출산·돌잔치/수연/취업·승진/개업·이사/장례식/제사·탈상 (영문 코드도 허용)", example = "결혼")
        String eventCategory,

        @Schema(description = "행사일 공통값. recordType=EVENT일 때만 쓰인다", example = "2026-08-15") LocalDate eventDate,

        @Schema(description = "카테고리 ID 공통값 (선물일 때만)", example = "1") Long categoryId,

        @Schema(description = "카테고리 이름 공통값 (선물일 때만)", example = "디저트") String category,

        @Schema(description = "받은 이유 공통값 (선물일 때만)", example = "내 생일") String occasion,

        @Schema(description = "선물명 공통값. 항목이 gift를 비우면 이 값을 쓴다", example = "축의금") String gift,

        @Schema(description = "금액 공통값. 하객마다 금액이 다르면 항목에서 각자 지정한다", example = "50,000원") String price,

        @Schema(description = "받은 날짜 공통값. 항목이 date를 비우면 이 값을 쓴다", example = "2026-08-15") LocalDate date,

        @Schema(description = "답례 알림일 공통값. 같은 행사·같은 답례일이면 구글 캘린더 일정도 하나로 묶인다",
                example = "2026-09-14")
        LocalDate reminderDate,

        @Schema(description = "감사 완료 여부 공통값. 생략하면 false", example = "false") Boolean thanked,

        @Schema(description = "저장할 사람들. 최소 1명, 최대 200명")
        @NotEmpty(message = "저장할 기록을 한 건 이상 보내주세요.")
        @Size(max = 200, message = "한 번에 저장할 수 있는 기록은 200건까지입니다.")
        @Valid List<GiftRecordBulkGuest> guests
) {
}
