package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 사진 없이 <b>직접 입력한 값</b>으로 AI 준비 작업을 돌리는 요청 ({@code POST /api/gift-records/prepare}).
 *
 * <p>필드는 기록 모달의 입력값과 같다 — {@link GiftRecordCreateRequest}에서 AI가 쓰는 것만 추린 것이다.
 * <b>저장 요청이 아니다.</b> 저장은 그대로 {@code POST /api/gift-records}로 한다.</p>
 */
@Schema(description = "AI 준비 작업 요청 (직접 입력값 기반). 저장하지 않고 추천·초안만 받아온다")
public record GiftRecordPrepareRequest(
        @Schema(description = "보낸 사람 Person ID. 주면 나이·성별·관계·이름을 등록된 값으로 채워 AI에 넘긴다", example = "3")
        Long personId,

        @Schema(description = "보낸 사람 이름. personId가 없을 때 쓴다", example = "김민수") String personName,

        @Schema(description = "관계. personId가 있으면 등록된 관계가 우선한다", example = "대학 동기") String relation,

        @Schema(description = "선물명", example = "스타벅스 케이크")
        @NotNull(message = "선물명을 입력해주세요.") String gift,

        @Schema(description = "금액. 숫자(35000)와 문자열(\"35,000원\") 모두 허용. "
                + "AI 명세상 0보다 커야 해서 0이나 빈 값이면 400으로 막는다", example = "35,000원")
        @NotNull(message = "금액을 입력해주세요.") String price,

        @Schema(description = "받은 날짜. 생략하면 오늘", example = "2026-08-19") LocalDate date,

        @Schema(description = "답례 예정일. AI의 캘린더·알림 초안이 이 날짜를 기준으로 만들어진다", example = "2026-09-10")
        LocalDate reminderDate,

        @Schema(description = "받은 이유 (자유 텍스트)", example = "내 생일") String occasion,

        @Schema(description = "추천 카드 개수 (기본 3, 최대 10)", example = "3") Integer limit
) {
}
