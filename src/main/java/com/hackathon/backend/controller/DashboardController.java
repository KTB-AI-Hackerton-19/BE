package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.dashboard.DashboardResponse;
import com.hackathon.backend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈 대시보드", description = "홈 화면 전체를 한 번의 호출로 그리기 위한 종합 API")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(
            summary = "홈 화면 데이터 조회",
            description = "홈 화면에 필요한 네 덩어리를 한 번에 내려준다. "
                    + "(1) stats — 상단 통계 카드 3개(기록한 마음 + 이번 달 증가분 / 소중한 사람 / 다가오는 일정 + 가장 가까운 일정까지 남은 일수). "
                    + "value·detail 문자열까지 서버가 완성해두어 그대로 출력하면 된다. "
                    + "(2) agentInsight — '마음 에이전트가 발견했어요' 카드. 다가오는 생일과 답례일 중 더 가까운 하나를 골라 "
                    + "제목/본문/캘린더 위젯 라벨까지 만들어준다. 해당 없으면 null이므로 카드를 숨기면 된다. "
                    + "(3) recentRecords — 최근 받은 마음(기본 4건). (4) recommendations — 선물 추천(기본 3건, agentInsight 대상 기준)."
    )
    @GetMapping
    public ApiResponse<DashboardResponse> get(
            @Parameter(description = "최근 받은 마음 개수 (기본 4, 최대 20)", example = "4")
            @RequestParam(required = false) Integer recentLimit,
            @Parameter(description = "선물 추천 개수 (기본 3, 최대 10)", example = "3")
            @RequestParam(required = false) Integer recommendationLimit) {
        return ApiResponse.success(dashboardService.getDashboard(recentLimit, recommendationLimit));
    }
}
