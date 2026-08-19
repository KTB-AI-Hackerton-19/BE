package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 서비스 {@code POST /api/v1/agent/from-image} 요청 바디.
 *
 * <p>클라이언트가 presigned PUT URL로 S3에 직접 올린 뒤 백엔드에 imageKey를 주면, 백엔드가 그 key로
 * 조회용 presigned GET URL을 만들어 여기에 실어 보낸다. AI 서비스는 이 URL로 이미지를 직접 내려받는다.</p>
 *
 * <p>필드명이 스네이크케이스({@code image_url})인 것은 AI 서비스(FastAPI) 스펙을 그대로 따른 것이다.</p>
 */
public record AiExtractRequest(
        @JsonProperty("image_url") String imageUrl
) {
}
