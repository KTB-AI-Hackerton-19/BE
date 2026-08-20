package com.hackathon.backend.client;

import com.hackathon.backend.domain.GiftKind;
import java.time.LocalDate;
import java.util.List;

/**
 * 이미지 한 장에 대한 AI 분석 결과 전체. <b>사진에 여러 명이 있으면 {@link #results()}에 사람 수만큼 담긴다.</b>
 *
 * <p>경조사 판정({@link #eventKind()})은 사람별이 아니라 사진 단위다 — 한 장의 축의금 목록이
 * 사람에 따라 결혼식이 됐다가 장례식이 될 수는 없기 때문이다.</p>
 *
 * @param eventKind 경조사면 CELEBRATION/CONDOLENCE, 일반 선물이면 {@link GiftKind#GIFT}
 * @param fallback  true면 AI가 아니라 <b>하드코딩 더미</b>다. 응답까지 그대로 올려보내 "AI가 잘 도는 줄 알았는데
 *                  실은 더미였다"를 눈으로 구분할 수 있게 한다
 */
public record AiExtractionBatch(
        List<AiExtractionResult> results,
        GiftKind eventKind,
        String eventName,
        LocalDate eventDate,
        boolean fallback,
        /** 폴백이면 왜 폴백했는지. AI가 준 에러 본문이 그대로 들어간다. 정상이면 null. */
        String fallbackReason
) {

    /** 사진에서 찾은 사람 수. */
    public int personCount() {
        return results.size();
    }

    /** 2명 이상인가 — 여러 명 확인 화면으로 갈지 판단하는 기준. */
    public boolean multiple() {
        return results.size() > 1;
    }

    public boolean isEvent() {
        return eventKind != null && eventKind.isEvent();
    }
}
