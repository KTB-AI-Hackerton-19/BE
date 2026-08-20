package com.hackathon.backend.gift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hackathon.backend.client.AiExtractResponse;
import com.hackathon.backend.domain.EventCategory;
import com.hackathon.backend.domain.GiftRecord;
import com.hackathon.backend.domain.RecordType;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.gift.GiftRecordExtractResponse;
import com.hackathon.backend.support.EventClassifier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 여러 명 추출 흐름에서 <b>계약이 깨지면 바로 티가 나는 것</b>만 검증한다.
 * AI 응답 모양(단건/배열/중첩)과, 기존 프론트가 읽는 최상위 평면 필드가 그대로 유지되는지.
 */
class ExtractMultiPersonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** 저장 전 DRAFT 한 건. DB를 태우지 않고 응답 직렬화만 보기 위한 최소 픽스처다. */
    private static GiftRecordResponse draftResponse(String senderName) {
        GiftRecord draft = GiftRecord.createDraft(null, null, "key.jpg", senderName, null, null, null,
                RecordType.GIFT, null, null, null, "결혼식", null, 100000,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 14));
        return GiftRecordResponse.from(draft, null);
    }

    @Test
    void 단건_객체도_원소_1개_목록으로_읽는다() {
        String json = """
                {"gift_data":{"status":"ok","payload":
                  {"person_name":"김민수","gift_price":35000,"received_at":"2026-08-18"}}}""";

        AiExtractResponse response = mapper.readValue(json, AiExtractResponse.class);

        assertEquals(1, response.payloads().size());
        assertEquals("김민수", response.payloads().get(0).personName());
    }

    @Test
    void payload가_배열이면_사람_수만큼_읽는다() {
        String json = """
                {"gift_data":{"payload":[
                  {"person_name":"김민수","gift_price":100000,"event":"결혼식"},
                  {"person_name":"이서연","gift_price":50000,"event":"결혼식"},
                  {"person_name":"박지훈","gift_price":30000,"event":"결혼식"}]}}""";

        AiExtractResponse response = mapper.readValue(json, AiExtractResponse.class);

        assertEquals(3, response.payloads().size());
        assertEquals("박지훈", response.payloads().get(2).personName());
    }

    @Test
    void 중첩_people_배열은_부모의_공통값을_물려받아_펼쳐진다() {
        String json = """
                {"gift_data":{"payload":{"event":"결혼식","event_date":"2026-08-15","received_at":"2026-08-15",
                  "people":[{"person_name":"김민수","gift_price":100000},
                            {"person_name":"이서연","gift_price":50000}]}}}""";

        List<AiExtractResponse.Payload> payloads = mapper.readValue(json, AiExtractResponse.class).payloads();

        assertEquals(2, payloads.size());
        assertEquals("결혼식", payloads.get(1).event());
        assertEquals("2026-08-15", payloads.get(1).eventDate().toString());
        assertEquals("2026-08-15", payloads.get(1).receivedAt().toString());
    }

    @Test
    void 카테고리_나이_성별_신뢰도까지_전부_읽는다() {
        String json = """
                {"gift_data":{"status":"success","payload":
                  {"person_name":"김민수","gift_name":"스타벅스 케이크","gift_price":35000,
                   "category":"디저트","age":32,"gender":"male","confidence":0.82}}}""";

        AiExtractResponse.Payload payload = mapper.readValue(json, AiExtractResponse.class).payloads().get(0);

        assertEquals("디저트", payload.category());
        assertEquals(32, payload.age());
        assertEquals("male", payload.gender());
        assertEquals(0.82, payload.confidence());
    }

    @Test
    void 실패_상태로_오면_payload가_있어도_실패로_본다() {
        String json = """
                {"gift_data":{"status":"failed","error":"이미지 분석에 실패했습니다.",
                  "payload":{"person_name":"김민수"}}}""";

        AiExtractResponse response = mapper.readValue(json, AiExtractResponse.class);

        assertTrue(response.failed());
        assertEquals("이미지 분석에 실패했습니다.", response.errorOrNull());
    }

    @Test
    void 경조사_판정은_유형을_가른다() {
        assertEquals(EventCategory.WEDDING, EventClassifier.classify("결혼식 축의금"));
        assertEquals(EventCategory.FUNERAL, EventClassifier.classify("아버지 장례식 조의금"));
        // 평범한 생일 선물이 경조사로 넘어가면 안 된다.
        assertNull(EventClassifier.classify("생일 축하"));
        assertNull(EventClassifier.classify((String) null));
        assertEquals("결혼식", EventClassifier.eventName(EventCategory.WEDDING, "결혼식"));
        assertEquals("장례식", EventClassifier.eventName(EventCategory.FUNERAL, null, " "));
    }

    @Test
    void 첫_번째_기록의_필드는_응답_최상위에_그대로_내려간다() {
        // 기존 프론트가 data.id / data.person을 그대로 읽고 있어서, 이게 깨지면 화면이 조용히 빈다.
        var first = draftResponse("김민수");
        var second = draftResponse("이서연");

        String json = mapper.writeValueAsString(GiftRecordExtractResponse.of(List.of(first, second), null));

        assertTrue(json.contains("\"person\":\"김민수\""), json);
        assertTrue(json.contains("\"personCount\":2"), json);
        assertTrue(json.contains("\"multiple\":true"), json);
        assertTrue(json.contains("\"records\":["), json);
        // 래퍼 필드가 중첩되어 들어가면(=@JsonUnwrapped가 안 먹으면) 최상위에 primary 키가 남는다.
        assertTrue(!json.contains("\"primary\""), json);
        assertNull(GiftRecordExtractResponse.of(List.of(first), null).eventCategory());
    }
}
