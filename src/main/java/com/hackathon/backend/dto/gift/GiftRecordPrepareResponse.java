package com.hackathon.backend.dto.gift;

import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * AI 준비 작업 결과. 전부 <b>초안</b>이고 <b>아무것도 저장되지 않는다</b>.
 *
 * <p>기록·답례 알림은 우리 DB가 만들고(=마음 기록 등록), 구글 캘린더 등록은 {@code /confirm}이 한다.
 * 여기서 실제로 새로 얻는 값은 <b>추천과 답례 메시지</b>이며, 나머지 두 초안은 AI가 준 그대로 통과시킨다.</p>
 */
@Schema(description = "AI 준비 작업 결과 (저장되지 않는 초안)")
public record GiftRecordPrepareResponse(
        @Schema(description = "추천 카드. 저장하지 않으므로 각 카드의 id는 항상 null이다 "
                + "(저장된 추천은 GET /api/recommendations 쪽)")
        List<RecommendationResponse> recommendations,

        @Schema(description = "AI가 써준 답례 인사 문구 (없으면 null)",
                example = "민수님, 챙겨주신 마음 덕분에 큰 힘이 됐어요.") String thankYouMessage,

        @Schema(description = "AI가 제안한 캘린더 일정 초안. 등록된 것이 아니다 — 실제 등록은 답례일 저장 시 자동으로 이뤄진다. "
                + "AI 쪽 스키마가 아직 유동적이라 받은 payload를 그대로 통과시킨다")
        Map<String, Object> calendarDraft,

        @Schema(description = "AI가 제안한 알림 초안. 우리 답례 알림(ReminderTask)과는 별개다")
        Map<String, Object> notiDraft,

        @Schema(description = "AI 워크플로 ID. /confirm으로 확정할 때 그대로 돌려줘야 하는 값", example = "wf_01H...")
        String workflowId,

        @Schema(description = "true면 AI가 '사용자 확인 후 확정'을 요구한 것", example = "false")
        boolean requiresConfirmation,

        @Schema(description = "AI 호출이 실패했거나 일부 블록이 실패했을 때의 사유. 성공이면 null. "
                + "<b>이 경로는 더미로 감추지 않으므로</b> 값이 있으면 추천이 비어 있을 수 있다",
                example = "AI 502: upstream timeout")
        String aiError
) {
}
