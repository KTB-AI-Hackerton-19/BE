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
                    + "'다시 추천받기' 버튼에서는 refresh=true로 호출한다. 이때 서버는 이 화면을 그릴 때 이미 백그라운드에서 "
                    + "만들어 둔 '다음 세트'를 그대로 올려주므로 대부분 AI를 기다리지 않고 즉시 응답한다 "
                    + "(미리받기가 아직 안 끝났거나 실패했으면 그 자리에서 새로 생성하므로 결과는 항상 나온다). "
                    + "응답을 내려보낸 뒤에는 다시 그다음 세트를 미리 만들어 두므로 버튼을 연달아 눌러도 같은 속도가 유지된다. "
                    + "AI_SERVICE_URL이 설정되지 않았거나 호출이 실패하면 하드코딩 더미 결과를 반환하므로 지금도 화면을 붙일 수 있다."
    )
    @GetMapping
    public ApiResponse<List<PersonRecommendationResponse>> list(
            @Parameter(description = "사람 한 명당 추천 선물 개수 (기본 3, 최대 10)", example = "3")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "true면 저장된 추천을 버리고 새로 생성 ('다시 추천받기')", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean refresh,

            @Parameter(description = "돌려줄 사람 그룹 수 상한. 홈처럼 한 명만 보여주는 화면은 1을 넘긴다. "
                    + "생략하면 같은 날짜의 대상 전원. 그룹마다 AI를 한 번씩 부르므로 refresh와 함께 쓸 땐 꼭 지정할 것",
                    example = "1")
            @RequestParam(required = false) Integer groups) {
        return ApiResponse.success(recommendationService.list(
                limit, refresh, groups == null ? RecommendationService.NO_GROUP_LIMIT : groups));
    }
}
