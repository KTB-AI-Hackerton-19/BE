package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.person.PersonDetailResponse;
import com.hackathon.backend.dto.person.PersonDeleteResponse;
import com.hackathon.backend.dto.person.PersonRequest;
import com.hackathon.backend.dto.person.PersonResponse;
import com.hackathon.backend.dto.recommendation.RecommendationResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.service.GiftRecordService;
import com.hackathon.backend.service.PersonService;
import com.hackathon.backend.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사람", description = "마음을 주고받는 상대방. 사람들 화면의 목록과 상세(타임라인)를 담당")
@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;
    private final GiftRecordService giftRecordService;
    private final RecommendationService recommendationService;

    public PersonController(PersonService personService, GiftRecordService giftRecordService,
                            RecommendationService recommendationService) {
        this.personService = personService;
        this.giftRecordService = giftRecordService;
        this.recommendationService = recommendationService;
    }

    @Operation(
            summary = "사람 목록 조회",
            description = "사람들 화면의 목록. 각 항목에 마음 개수(giftCount), 최근 받은 선물(latestGift), "
                    + "가장 가까운 답례 알림일(upcomingReminderDate)까지 채워서 내려주므로 추가 호출이 필요 없다. "
                    + "q를 주면 이름으로 검색한다. 사람이 많아질 수 있어 페이징된다(기본 30건)."
    )
    @GetMapping
    public ApiResponse<PageResponse<PersonResponse>> list(
            @Parameter(description = "이름 검색어 (부분 일치)", example = "민수") @RequestParam(required = false) String q,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(personService.search(q, page, size));
    }

    @Operation(
            summary = "사람 상세 조회",
            description = "사람 상세 화면 전체를 한 번에 그릴 수 있는 응답. 요약(이름/관계/마음 개수/최근 받은 날/다가오는 알림일)과 "
                    + "주고받은 마음 타임라인(받은 날짜 최신순)을 함께 내려준다."
    )
    @GetMapping("/{id}")
    public ApiResponse<PersonDetailResponse> get(
            @Parameter(description = "조회할 사람 ID") @PathVariable Long id) {
        PersonResponse person = personService.get(id);
        List<GiftRecordResponse> records = giftRecordService.listByPerson(id);
        return ApiResponse.success(new PersonDetailResponse(person, records));
    }

    @Operation(
            summary = "사람별 마음 타임라인 조회",
            description = "특정 사람에게 받은 마음만 받은 날짜 최신순으로 조회한다. 상세 화면의 타임라인만 다시 불러올 때 사용."
    )
    @GetMapping("/{id}/gift-records")
    public ApiResponse<PageResponse<GiftRecordResponse>> records(
            @Parameter(description = "조회할 사람 ID") @PathVariable Long id,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size) {
        personService.get(id); // 소유권 검증 (다른 사용자의 사람이면 404)
        return ApiResponse.success(giftRecordService.listByPerson(id, page, size));
    }

    @Operation(
            summary = "사람별 선물 추천 조회",
            description = "특정 사람 한 명을 지정해 그 사람의 관계·메모·지난 선물을 근거로 만든 추천 선물 목록을 조회한다. "
                    + "한 번 생성된 추천은 저장해두고 재사용하므로 화면 진입 때마다 AI를 다시 부르지 않는다. "
                    + "'다시 추천받기' 버튼에서는 refresh=true로 호출하면 기존 추천을 버리고 새로 생성한다. "
                    + "AI_SERVICE_URL이 설정되지 않았거나 호출이 실패하면 하드코딩 더미 결과를 반환하므로 지금도 화면을 붙일 수 있다."
    )
    @GetMapping("/{id}/recommendations")
    public ApiResponse<List<RecommendationResponse>> recommendations(
            @Parameter(description = "추천 대상 사람 ID") @PathVariable Long id,
            @Parameter(description = "추천 개수 (기본 3, 최대 3 — 화면이 3열 한 줄이라 상한을 3으로 고정)", example = "3") @RequestParam(required = false) Integer limit,
            @Parameter(description = "true면 저장된 추천을 버리고 새로 생성 ('다시 추천받기')", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ApiResponse.success(recommendationService.listForPerson(id, limit, refresh));
    }

    @Operation(
            summary = "사람 등록",
            description = "상대방을 미리 등록한다. 기록 저장 시 이름만 보내도 사람이 자동 생성되므로 필수는 아니고, "
                    + "생일/메모를 미리 넣어두면 홈 화면 에이전트 카드(다가오는 생일)와 선물 추천 품질에 반영된다. "
                    + "같은 이름이 이미 있으면 새로 만들지 않고 기존 사람의 정보를 갱신한다."
    )
    @PostMapping
    public ApiResponse<PersonResponse> create(@Valid @RequestBody PersonRequest request) {
        return ApiResponse.success(personService.create(request));
    }

    @Operation(
            summary = "사람 정보 수정",
            description = "이름/관계/생일/메모를 수정한다. 보내지 않은 필드는 기존 값이 유지된다."
    )
    @PatchMapping("/{id}")
    public ApiResponse<PersonResponse> update(
            @Parameter(description = "수정할 사람 ID") @PathVariable Long id,
            @RequestBody PersonRequest request) {
        return ApiResponse.success(personService.update(id, request));
    }

    @Operation(
            summary = "사람 삭제 (단일)",
            description = "그 사람의 마음 기록·답례 알림·선물 추천이 함께 삭제된다. 기록이 남아 있어도 막지 않는다. "
                    + "없는 ID면 404. 응답으로 함께 지워진 건수를 돌려주므로 "
                    + "\"김민수님과 기록 3건을 삭제했어요\" 같은 안내에 그대로 쓰면 된다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<PersonDeleteResponse> delete(
            @Parameter(description = "삭제할 사람 ID") @PathVariable Long id) {
        return ApiResponse.success(personService.delete(id));
    }

    @Operation(
            summary = "사람 삭제 (다중 / 전체)",
            description = "목록에서 여러 명을 체크해 한 번에 지울 때는 ?ids=1,2,3, 내 사람을 전부 비울 때는 ?all=true. "
                    + "둘 중 하나만 보내야 하고, 아무것도 안 보내거나 둘 다 보내면 400이다. "
                    + "어느 쪽이든 각 사람의 마음 기록·답례 알림·선물 추천이 함께 삭제되고, 실제로 지워진 건수를 돌려준다. "
                    + "ids 방식에서 이미 지워졌거나 다른 사용자의 ID는 오류 없이 건너뛴다. "
                    + "**사람이 지정되지 않은 기록(경조사 하객 등 이름만 있는 건)은 남는다** — 지울 사람이 없기 때문이다. "
                    + "계정까지 지우려면 DELETE /api/users(회원탈퇴)를 쓴다. all=true는 되돌릴 수 없으니 "
                    + "화면에서 한 번 확인받고 호출할 것."
    )
    @DeleteMapping
    public ApiResponse<PersonDeleteResponse> deleteAll(
            @Parameter(description = "삭제할 사람 ID 목록 (예: ?ids=1,2,3)")
            @RequestParam(required = false) List<Long> ids,
            @Parameter(description = "true면 내 사람 전체를 삭제한다. ids와 함께 보낼 수 없다", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            if (ids != null && !ids.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "ids와 all=true는 함께 보낼 수 없습니다.");
            }
            return ApiResponse.success(personService.deleteAllOfUser());
        }
        return ApiResponse.success(personService.deleteAll(ids));
    }
}
