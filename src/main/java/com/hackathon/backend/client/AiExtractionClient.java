package com.hackathon.backend.client;

import com.hackathon.backend.domain.GiftKind;
import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.support.EventClassifier;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionClient.class);

    private static final String EXTRACT_PATH = "/api/v1/agent/from-image";

    /** 이 값 밑이면 "AI가 확신 없이 찍었다"고 보고 로그를 남긴다. */
    private static final double LOW_CONFIDENCE = 0.5;

    private final RestClient restClient;
    private final String aiServiceUrl;
    /** false면 더미로 감추지 않고 그대로 실패시킨다(연동 디버깅용). */
    private final boolean fallbackEnabled;

    public AiExtractionClient(@Value("${ai.service.url}") String aiServiceUrl,
                              @Value("${ai.service.api-key}") String apiKey,
                              @Value("${ai.service.timeout-ms}") int timeoutMs,
                              @Value("${ai.service.fallback-enabled:true}") boolean fallbackEnabled) {
        this.aiServiceUrl = aiServiceUrl;
        this.fallbackEnabled = fallbackEnabled;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-API-KEY", apiKey == null ? "" : apiKey)
                .defaultHeader("ngrok-skip-browser-warning", "1")
                .build();
    }

    public AiExtractionBatch extract(String imageReadUrl) {
        if (aiServiceUrl == null || aiServiceUrl.isBlank()) {
            return fallbackOrFail("AI_SERVICE_URL이 설정되지 않았습니다.");
        }

        try {
            AiExtractResponse response = restClient.post()
                    .uri(trimTrailingSlash(aiServiceUrl) + EXTRACT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AiExtractRequest(imageReadUrl))
                    .retrieve()
                    .body(AiExtractResponse.class);

            List<AiExtractResponse.Payload> payloads = response == null ? List.of() : response.payloads();
            if (payloads.isEmpty()) {
                // AI는 실패해도 200 + gift_data.error로 돌려준다. 그 사유를 그대로 실어 보낸다.
                return fallbackOrFail(response != null && response.errorOrNull() != null
                        ? "AI 분석 실패: " + response.errorOrNull()
                        : "AI 응답에 gift_data가 없습니다.");
            }
            if (response.failed()) {
                return fallbackOrFail("AI가 실패 상태(status=%s)로 응답했습니다: %s"
                        .formatted(response.giftData().status(), response.errorOrNull()));
            }
            return toBatch(payloads);
        } catch (RestClientResponseException e) {
            // 응답 본문까지 남긴다. AI 쪽 실패 원인("이미지 분석에 실패했습니다" 등)이 여기 들어 있다.
            return fallbackOrFail("AI %s: %s".formatted(e.getStatusCode(), e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            return fallbackOrFail("AI 호출 실패: " + e.getMessage());
        }
    }

    /**
     * 사람별 payload 목록을 배치로 만든다. 경조사 판정은 <b>사진 단위</b>라, 사람들의 경조사명(event)을
     * 전부 합쳐서 한 번만 분류한다 (한 명만 "축의금"이라고 적혀 있어도 그 사진 전체가 경조사다).
     *
     * <p>판정 근거는 경조사명(event)과 카테고리다. 선물명은 "부조금·축의금"처럼 그 자체가 경조사인 말일 때만
     * 본다 — 전부 보면 "생일 축하 케이크" 같은 평범한 선물이 경조사로 넘어간다.</p>
     */
    private AiExtractionBatch toBatch(List<AiExtractResponse.Payload> payloads) {
        List<AiExtractionResult> results = payloads.stream()
                .map(p -> new AiExtractionResult(
                        p.personName(), p.relationship(), p.receivedAt(), p.event(),
                        p.giftName(), p.category(), p.giftPrice(),
                        p.age(), p.gender(), p.confidence(),
                        p.event(), p.eventDate()))
                .toList();

        // confidence는 저장하지 않는다(화면에 쓸 곳이 아직 없다). 대신 낮은 건은 로그로 남겨,
        // "AI가 확신 없이 찍은 값"이 그대로 확정되는 상황을 나중에 추적할 수 있게 한다.
        results.stream()
                .filter(r -> r.confidence() != null && r.confidence() < LOW_CONFIDENCE)
                .forEach(r -> log.warn("AI 신뢰도 낮음 — 사용자 확인 필요. 이름={} 신뢰도={}", r.senderName(), r.confidence()));

        StringBuilder signals = new StringBuilder();
        StringBuilder giftNames = new StringBuilder();
        LocalDate eventDate = null;
        String eventName = null;
        for (AiExtractResponse.Payload p : payloads) {
            signals.append(nullToEmpty(p.event())).append(' ').append(nullToEmpty(p.category())).append(' ');
            giftNames.append(nullToEmpty(p.giftName())).append(' ');
            if (eventDate == null) {
                eventDate = p.eventDate();
            }
            if (eventName == null && p.event() != null && !p.event().isBlank()) {
                eventName = p.event();
            }
        }
        GiftKind kind = EventClassifier.classify(signals.toString());
        if (!kind.isEvent()) {
            // 경조사명이 비어 있어도 선물명이 "부조금"이면 경조사다. 이 경우만 선물명을 본다.
            kind = EventClassifier.classifyGiftName(giftNames.toString());
            if (kind.isEvent() && eventName == null) {
                // 이벤트 이름이 없으면 "경사" 같은 라벨보다 "부조금"이 카드에 낫다. 사용자가 나중에 바꾸면 된다.
                eventName = payloads.getFirst().giftName();
            }
        }
        log.info("AI 분석 완료 — 사람 {}명, 분류 {}{}", results.size(), kind,
                kind.isEvent() ? " (경조사: %s)".formatted(EventClassifier.eventName(kind, eventName)) : "");

        return new AiExtractionBatch(results, kind,
                kind.isEvent() ? EventClassifier.eventName(kind, eventName) : null, eventDate, false, null);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 폴백이 켜져 있으면 더미에 실패 사유를 달아 돌려주고, 꺼져 있으면 그대로 502로 실패시킨다.
     * 연동 확인 중에는 {@code ai.service.fallback-enabled=false}로 두면 원인이 응답에 바로 나온다.
     */
    private AiExtractionBatch fallbackOrFail(String reason) {
        if (!fallbackEnabled) {
            log.error("AI 분석 실패 — 폴백이 꺼져 있어 그대로 실패시킵니다. 사유: {}", reason);
            throw new CustomException(ErrorCode.AI_SERVICE_ERROR, reason);
        }
        log.error("AI 분석 실패 — 더미 결과로 대체합니다. 화면 값은 AI 결과가 아닙니다. 사유: {}", reason);
        return dummyBatch(reason);
    }

    /** 더미는 항상 1명이다. 여러 명 흐름은 실제 AI 응답에서만 나온다. */
    private AiExtractionBatch dummyBatch(String reason) {
        // 관계는 드롭다운에 실제로 있는 값이어야 한다. 자유 표현("친한 친구")을 쓰면 서버가 맞춰주지 않으므로
        // 확인 폼의 관계가 늘 비어서, AI 연동이 죽었을 때 그것까지 버그로 보이게 된다.
        AiExtractionResult dummy = new AiExtractionResult(
                "김민수", Relationship.FRIEND.getLabel(), LocalDate.now(), "내 생일",
                "스타벅스 케이크", "디저트", 35000, null, null, null, null, null);
        return new AiExtractionBatch(List.of(dummy), GiftKind.GIFT, null, null, true, reason);
    }
}
