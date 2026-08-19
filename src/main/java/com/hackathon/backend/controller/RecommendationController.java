package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.recommendation.PersonRecommendationResponse;
import com.hackathon.backend.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "선물 추천", description = "홈 화면 '이런 선물은 어때요?' 카드. AI 서비스가 없으면 더미 3건으로 폴백")
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(
            summary = "선물 추천 조회",
            description = "홈 화면의 추천 카드 목록. 대상은 직접 지정하지 않고, 답례 알림(reminderDate)이 가장 가까운 "
                    + "'날짜'를 찾은 뒤, 그 날짜에 답례할 사람 전원에 대해 각각 추천 선물 목록을 묶어서 반환한다 "
                    + "(예: [{person: 민수, gifts: [...]}, {person: 지은, gifts: [...]}] — 같은 날 답례할 사람이 여럿이면 그만큼 묶여 나온다). "
                    + "답례 예정인 사람이 아무도 없으면 특정 대상 없는 일반 추천 그룹 하나로 대체한다. "
                    + "한 번 생성된 추천은 대상별로 저장해두고 재사용하므로 화면 진입 때마다 AI를 다시 부르지 않는다. "
                    + "'다시 추천받기' 버튼에서는 refresh=true로 호출하면 기존 추천을 버리고 새로 생성한다. "
                    + "AI_SERVICE_URL이 설정되지 않았거나 호출이 실패하면 하드코딩 더미 결과를 반환하므로 지금도 화면을 붙일 수 있다."
    )
    @GetMapping
    public ApiResponse<List<PersonRecommendationResponse>> list(
            @Parameter(description = "사람 한 명당 추천 선물 개수 (기본 3, 최대 10)", example = "3")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "true면 저장된 추천을 버리고 새로 생성 ('다시 추천받기')", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ApiResponse.success(recommendationService.list(limit, refresh));
    }
}
