package com.hackathon.backend.dto.dashboard;

import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 홈 화면 전체를 한 번의 호출로 그리기 위한 응답. 통계 / 에이전트 카드 / 최근 마음 / 추천을 모두 담는다. */
@Schema(description = "홈 화면 종합 응답")
public record DashboardResponse(
        @Schema(description = "서버 기준 오늘 날짜 (상단 인사말의 날짜 표기에 사용)", example = "2026-08-18") LocalDate today,
        @Schema(description = "상단 통계 카드 3종") DashboardStatsResponse stats,
        @Schema(description = "'마음 에이전트가 발견했어요' 카드. 다가오는 일정이 없으면 null") AgentInsightResponse agentInsight,
        @Schema(description = "최근 받은 마음 (기본 4건, recentLimit 파라미터로 조절)") List<GiftRecordResponse> recentRecords,
        @Schema(description = "선물 추천 (기본 3건). agentInsight의 대상 인물 기준으로 생성된다") List<RecommendationResponse> recommendations
) {
}
