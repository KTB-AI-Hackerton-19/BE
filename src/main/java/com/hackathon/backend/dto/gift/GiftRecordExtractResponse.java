package com.hackathon.backend.dto.gift;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code POST /api/gift-records/extract} 응답. <b>사진 한 장에서 여러 명이 나오면 사람 수만큼 DRAFT가 만들어지고
 * records에 전부 담겨 내려간다.</b>
 *
 * <p>기존 프론트가 {@code data.id} / {@code data.date}처럼 <b>기록 필드를 최상위에서 바로 읽고 있어서</b>,
 * 첫 번째 기록을 {@link JsonUnwrapped}로 최상위에 그대로 펼쳐 둔다. 덕분에 여러 명 화면을 아직 안 만든
 * 상태에서도 지금 프론트가 그대로 동작하고(첫 번째 사람 = 지금까지와 같은 단건 응답),
 * 새 화면은 {@code records}만 보면 된다.</p>
 *
 * <pre>
 * {
 *   "id": 12, "person": "김민수", "date": "2026-08-20", ...   // ← 첫 번째 기록 (기존과 동일)
 *   "personCount": 3,
 *   "multiple": true,
 *   "records": [ {...}, {...}, {...} ],
 *   "eventCategory": { "name": "WEDDING", "label": "결혼", "group": "CELEBRATION", ... }
 * }
 * </pre>
 */
@Schema(description = "AI 이미지 분석 결과. 여러 명이면 records에 사람 수만큼 DRAFT가 담긴다")
public record GiftRecordExtractResponse(

        /** 하위호환용. 첫 번째 기록의 필드가 최상위에 그대로 펼쳐진다(records[0]과 같은 객체). */
        @JsonUnwrapped @Schema(hidden = true) GiftRecordResponse primary,

        @Schema(description = "사진에서 찾은 사람 수. AI가 준 사람 목록의 길이 그대로다", example = "3")
        int personCount,

        @Schema(description = "2명 이상이면 true. 프론트는 이 값으로 단건 확인 폼과 여러 명 확인 목록을 가른다",
                example = "true")
        boolean multiple,

        @Schema(description = "사람별 DRAFT 기록 전체. 1명이면 원소 1개다. 각 항목을 사용자가 확인한 뒤 "
                + "PATCH /api/gift-records/{id}로 하나씩 확정하면 된다")
        List<GiftRecordResponse> records,

        @Schema(description = "경조사로 판정됐을 때 이 기록들의 유형(고정 7종 중 하나). 경조사가 아니면 null이다")
        EventCategoryResponse eventCategory
) {

    public static GiftRecordExtractResponse of(List<GiftRecordResponse> records, EventCategoryResponse eventCategory) {
        return new GiftRecordExtractResponse(
                records.isEmpty() ? null : records.get(0),
                records.size(),
                records.size() > 1,
                records,
                eventCategory);
    }
}
