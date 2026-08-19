package com.hackathon.backend.client;

/**
 * AI 서비스로 보내는 분석 요청 바디. 필드는 {@code imageUrl} 하나로 확정.
 *
 * <p>클라이언트가 presigned PUT URL로 S3에 직접 올린 뒤 백엔드에 imageKey를 주면, 백엔드가 그 key로
 * 조회용 presigned GET URL을 만들어 여기에 실어 보낸다. AI 서비스는 이 URL로 이미지를 직접 내려받는다.
 * (S3 key나 raw bytes를 보내는 방식은 쓰지 않는다.)</p>
 */
public record AiExtractRequest(
        String imageUrl
) {
}
