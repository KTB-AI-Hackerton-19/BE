package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
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
            description = "홈 화면의 추천 카드 목록. personId를 주면 그 사람의 관계·메모·지난 선물을 근거로 추천하고, "
                    + "생략하면 사용자 전체 기록 기준으로 추천한다. "
                    + "한 번 생성된 추천은 저장해두고 재사용하므로 화면 진입 때마다 AI를 다시 부르지 않는다. "
                    + "'다시 추천받기' 버튼에서는 refresh=true로 호출하면 기존 추천을 버리고 새로 생성한다. "
                    + "AI_SERVICE_URL이 설정되지 않았거나 호출이 실패하면 하드코딩 더미 3건을 반환하므로 지금도 화면을 붙일 수 있다."
    )
    @GetMapping
    public ApiResponse<List<RecommendationResponse>> list(
            @Parameter(description = "추천 대상 Person ID (생략 시 전체 기록 기준)", example = "3")
            @RequestParam(required = false) Long personId,
            @Parameter(description = "추천 개수 (기본 3, 최대 10)", example = "3") @RequestParam(required = false) Integer limit,
            @Parameter(description = "true면 저장된 추천을 버리ㅌ고 새로 생성 ('다시 추천받기')", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ApiResponse.success(recommendationService.list(personId, limit, refresh));
    }
}
