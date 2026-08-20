package com.hackathon.backend.controller;

import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.EventCategoryResponse;
import com.hackathon.backend.dto.gift.GiftRecordBulkConfirmRequest;
import com.hackathon.backend.dto.gift.GiftRecordBulkCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordBulkResponse;
import com.hackathon.backend.dto.gift.GiftRecordCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordDeleteResponse;
import com.hackathon.backend.dto.gift.GiftRecordExtractRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractResponse;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkRequest;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkResponse;
import com.hackathon.backend.dto.gift.GiftRecordPrepareRequest;
import com.hackathon.backend.dto.gift.GiftRecordPrepareResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.gift.GiftRecordThankedRequest;
import com.hackathon.backend.dto.gift.GiftRecordUpdateRequest;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.service.GiftDataAgentService;
import com.hackathon.backend.service.GiftRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "마음 기록", description = "받은 선물·부조금 기록. 사진 업로드 → AI 분석 → 확인/수정 → 저장 흐름과 목록/상세/삭제")
@RestController
@RequestMapping("/api/gift-records")
public class GiftRecordController {

    private final GiftRecordService giftRecordService;
    private final GiftDataAgentService giftDataAgentService;

    public GiftRecordController(GiftRecordService giftRecordService, GiftDataAgentService giftDataAgentService) {
        this.giftRecordService = giftRecordService;
        this.giftDataAgentService = giftDataAgentService;
    }

    @Operation(
            summary = "마음 기록 목록 조회",
            description = "마음 기록 화면의 목록. 카테고리 필터, 사람 필터, 기간 필터, 검색어, 감사 완료 여부로 걸러낼 수 있고 "
                    + "기본 정렬은 받은 날짜 최신순이다. totalElements를 '{N}개의 마음' 문구에 그대로 쓰면 된다."
    )
    @GetMapping
    public ApiResponse<PageResponse<GiftRecordResponse>> list(
            @Parameter(description = "카테고리 ID 필터", example = "1") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "카테고리 이름 필터. '전체'를 보내면 필터를 적용하지 않는다", example = "디저트")
            @RequestParam(required = false) String category,
            @Parameter(description = "특정 사람이 준 것만 보기", example = "3") @RequestParam(required = false) Long personId,
            @Parameter(description = "감사 완료 여부 필터 (true: 감사 완료만, false: 확인 필요만)") @RequestParam(required = false) Boolean thanked,
            @Parameter(description = "상태 필터. 생략하면 DRAFT/CONFIRMED 모두 조회") @RequestParam(required = false) GiftRecordStatus status,
            @Parameter(description = "분류 필터. EVENT(경조사 전체) / GIFT(선물) / CELEBRATION(경사) / CONDOLENCE(조사) / "
                    + "구체 유형(예: 결혼, WEDDING). 한글도 허용. 생략하면 전체", example = "EVENT")
            @RequestParam(required = false) String kind,
            @Parameter(description = "받은 날짜 시작 (이 날짜 포함)", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "받은 날짜 끝 (이 날짜 포함)", example = "2026-08-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "검색어 — 선물명/받은 이유/보낸 사람 이름을 부분 일치로 찾는다", example = "케이크")
            @RequestParam(required = false) String q,
            @Parameter(description = "보낸 사람 이름으로만 좁히기. 통합 검색(q)과 달리 선물명·이유는 보지 않는다. "
                    + "경조사 이벤트처럼 한 목록에 수십 명이 있을 때 특정 사람을 찾는 용도", example = "김민수")
            @RequestParam(required = false) String personName,
            @Parameter(description = "정렬. latest(받은 날짜 최신순, 기본) / oldest / amount(금액 큰 순) / created(등록 최신순)",
                    example = "latest") @RequestParam(required = false, defaultValue = "latest") String sort,
            @Parameter(description = "페이지 번호 (0부터)", example = "0") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)", example = "20") @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResponse.success(giftRecordService.search(
                categoryId, category, personId, thanked, status, kind, startDate, endDate, q, personName,
                sort, page, size));
    }

    @Operation(
            summary = "마음 기록 상세 조회",
            description = "목록/캘린더에서 항목을 눌렀을 때 쓰는 단건 조회. 다른 사용자의 기록이면 404를 반환한다."
    )
    @GetMapping("/{id}")
    public ApiResponse<GiftRecordResponse> get(
            @Parameter(description = "조회할 기록 ID") @PathVariable Long id) {
        return ApiResponse.success(giftRecordService.get(id));
    }

    @Operation(
            summary = "마음 기록 등록",
            description = "기록 모달의 확인/수정 폼을 그대로 저장하거나, 사진 없이 직접 등록할 때 사용한다. "
                    + "보낸 사람은 personId로 지정해도 되고 personName(+relation)만 보내도 된다 — 없는 이름이면 사람이 자동 등록된다. "
                    + "reminderDate를 함께 보내면 답례 알림이 자동 생성된다."
    )
    @PostMapping
    public ApiResponse<GiftRecordResponse> create(@Valid @RequestBody GiftRecordCreateRequest request) {
        return ApiResponse.success(giftRecordService.create(request));
    }

    @Operation(
            summary = "마음 기록 여러 건 등록 (하객 명단 한 번에)",
            description = "경조사 하객처럼 **여러 명을 한 요청으로** 저장한다. 단건 등록을 사람 수만큼 반복하면 "
                    + "요청이 그 수만큼 날아가고, 동시에 들어온 요청들이 서로를 못 봐서 같은 행사의 구글 캘린더 일정이 "
                    + "하객 수만큼 생기는 문제가 있었다.\n\n"
                    + "행사 정보(recordType/eventCategory/eventDate/date/reminderDate/gift/price)는 **최상위에 한 번**만 적고, "
                    + "guests에는 사람마다 다른 값만 넣으면 된다 — 비운 칸은 최상위 공통값을 그대로 물려받는다. "
                    + "등록된 사람은 personId로, 하객처럼 '사람들'에 올리지 않을 이름은 guestName으로 보낸다.\n\n"
                    + "**전부 저장되거나 전부 저장되지 않는다.** 한 건이라도 문제가 있으면 아무것도 저장하지 않고 400이며, "
                    + "몇 번째 항목의 어느 칸이 문제인지 error.fields에 `guests[2].price` 형태로 모아서 내려간다."
    )
    @PostMapping("/bulk")
    public ApiResponse<GiftRecordBulkResponse> createBulk(@Valid @RequestBody GiftRecordBulkCreateRequest request) {
        return ApiResponse.success(giftRecordService.createBulk(request));
    }

    @Operation(
            summary = "DRAFT 여러 건 확정 (사진 한 장에 여러 명)",
            description = "`/extract`가 만든 DRAFT 여러 건을 **한 요청으로** 확정한다. "
                    + "PATCH /api/gift-records/{id}를 사람 수만큼 반복하던 것을 대체한다.\n\n"
                    + "이름·금액·받은 날짜처럼 **사람마다 다른 값은 AI가 넣어 둔 것을 그대로 두고**, "
                    + "확인 폼에서 사용자가 고친 공통값(행사 유형·행사일·답례 알림일 등)만 전원에게 얹는다. "
                    + "값을 안 보낸 필드는 각 기록의 기존 값이 유지된다.\n\n"
                    + "등록과 마찬가지로 전부 확정되거나 전부 확정되지 않으며, 오류는 보낸 ids 순서 기준으로 "
                    + "`ids[1].eventCategory` 형태로 모아서 내려간다."
    )
    @PatchMapping("/bulk")
    public ApiResponse<GiftRecordBulkResponse> confirmBulk(@Valid @RequestBody GiftRecordBulkConfirmRequest request) {
        return ApiResponse.success(giftRecordService.confirmBulk(request));
    }

    @Operation(
            summary = "AI 이미지 분석 (DRAFT 생성, 여러 명 지원)",
            description = "기록 모달에서 사진을 올린 뒤 호출한다. presigned URL로 S3에 업로드된 imageKey를 받아 "
                    + "백엔드가 조회용 presigned GET URL을 만들어 AI 서비스에 imageUrl로 전달하고, 결과를 status=DRAFT로 저장한다.\n\n"
                    + "**사진에서 여러 명이 나오면 사람 수만큼 DRAFT가 만들어져 records에 전부 담긴다.** "
                    + "personCount가 사람 수, multiple이 2명 이상 여부다. 1명이면 records 원소가 1개이고, "
                    + "첫 번째 기록의 필드(person/date/occasion/gift/category/price ...)는 예전처럼 응답 최상위에도 그대로 있다.\n\n"
                    + "**대분류(recordType)는 사람 수로 정해진다 — 2명 이상이면 전원 EVENT(경조사), 1명이면 GIFT(선물).** "
                    + "EVENT일 때만 AI가 판정한 경조사 유형(고정 7종 중 하나)이 사람 전원에게 같이 붙고 응답의 eventCategory로 "
                    + "함께 내려간다(유형까지 못 집어냈으면 null이라 사용자가 확인 폼에서 고른다). GIFT면 eventCategory는 항상 null이다.\n\n"
                    + "AI 서비스가 아직 없으면(AI_SERVICE_URL 미설정) 하드코딩된 더미 결과 1건을 반환하므로 지금도 전체 흐름을 테스트할 수 있다."
    )
    @PostMapping("/extract")
    public ApiResponse<GiftRecordExtractResponse> extract(@Valid @RequestBody GiftRecordExtractRequest request) {
        return ApiResponse.success(giftRecordService.extract(request));
    }

    @Operation(
            summary = "AI 준비 작업 (사진 없이, 직접 입력값 기반)",
            description = "`/extract`의 텍스트 버전. 사진 대신 **이미 입력한 값**(선물명·금액·사람·날짜)을 그대로 AI에 넘겨 "
                    + "AI 서비스의 from-gift-data를 호출한다.\n\n"
                    + "**아무것도 저장하지 않는다.** 마음 기록 저장은 지금처럼 `POST /api/gift-records`가 하고, "
                    + "이 엔드포인트는 등록 화면에서 바로 보여줄 **추천 카드와 답례 메시지**를 받아오는 용도다 "
                    + "(캘린더·알림 초안도 함께 오지만 우리는 우리 DB와 /confirm으로 처리하므로 참고값이다).\n\n"
                    + "추천 카드 모양은 `GET /api/recommendations`의 gifts와 동일하다 — 저장하지 않으므로 id만 null이다. "
                    + "저장된 추천 캐시도 건드리지 않아서, 등록 중에 눌러봐도 홈 화면 추천은 그대로다.\n\n"
                    + "**이 경로는 더미로 폴백하지 않는다.** AI가 죽어 있으면 recommendations가 비고 aiError에 사유가 담긴다 "
                    + "(연동 확인 중에 더미가 성공처럼 보이는 상황을 막기 위함)."
    )
    @PostMapping("/prepare")
    public ApiResponse<GiftRecordPrepareResponse> prepare(@Valid @RequestBody GiftRecordPrepareRequest request) {
        return ApiResponse.success(giftDataAgentService.prepare(request));
    }

    @Operation(
            summary = "경조사 유형 목록",
            description = "기록 모달에서 recordType=EVENT를 고를 때 쓰는 eventCategory select 옵션. "
                    + "선물 카테고리와 달리 고정 7종이라 추가/삭제 API가 없다."
    )
    @GetMapping("/event-categories")
    public ApiResponse<List<EventCategoryResponse>> eventCategories() {
        return ApiResponse.success(giftRecordService.listEventCategories());
    }

    @Operation(
            summary = "마음 기록 수정 / 확정",
            description = "AI가 만든 DRAFT를 사용자가 확인·수정해 저장(확정)하거나, 이미 저장된 기록을 나중에 고칠 때 사용한다. "
                    + "보내지 않은 필드는 기존 값이 유지되는 부분 수정이며, confirm을 false로 주지 않는 한 status가 CONFIRMED로 바뀐다. "
                    + "reminderDate를 바꾸면 답례 알림도 함께 재조정된다."
    )
    @PatchMapping("/{id}")
    public ApiResponse<GiftRecordResponse> update(
            @Parameter(description = "수정할 기록 ID") @PathVariable Long id,
            @Valid @RequestBody GiftRecordUpdateRequest request) {
        return ApiResponse.success(giftRecordService.update(id, request));
    }

    @Operation(
            summary = "보낸 사람을 '사람들'에 연결",
            description = "이름만 있고 사람으로 등록되지 않은 기록(응답의 personId가 null인 건)을 Person에 연결한다. "
                    + "경조사는 하객 전원을 사람으로 만들지 않고 리스트에만 두는 게 기본이라, 사용자가 직접 고른 사람만 "
                    + "이 API로 뒤늦게 매핑한다. personId를 주면 기존 사람에 붙이고, 안 주면 기록에 적힌 이름으로 새로 만든다. "
                    + "딸린 답례 알림의 대상도 함께 갱신된다."
    )
    @PostMapping("/{id}/person")
    public ApiResponse<GiftRecordPersonLinkResponse> linkPerson(
            @Parameter(description = "연결할 기록 ID") @PathVariable Long id,
            @RequestBody(required = false) GiftRecordPersonLinkRequest request) {
        return ApiResponse.success(giftRecordService.linkPerson(
                id, request != null ? request : new GiftRecordPersonLinkRequest(null, null, null)));
    }

    @Operation(
            summary = "감사 완료 여부 토글",
            description = "기록 카드 우측의 '감사 완료' / '확인 필요' 뱃지를 바꾼다. 답례를 마쳤을 때 thanked=true로 보내면 된다."
    )
    @PatchMapping("/{id}/thanked")
    public ApiResponse<GiftRecordResponse> updateThanked(
            @Parameter(description = "대상 기록 ID") @PathVariable Long id,
            @Valid @RequestBody GiftRecordThankedRequest request) {
        return ApiResponse.success(giftRecordService.updateThanked(id, request.thanked()));
    }

    @Operation(
            summary = "마음 기록 삭제 (단일)",
            description = "기록과 여기에 연결된 답례 알림을 함께 삭제한다. 없는 ID면 404. "
                    + "보낸 사람(Person)은 지우지 않는다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "삭제할 기록 ID") @PathVariable Long id) {
        giftRecordService.delete(id);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "마음 기록 삭제 (다중 / 전체)",
            description = "목록에서 여러 건을 체크해 한 번에 지울 때는 ?ids=1,2,3, 내 기록을 전부 비울 때는 ?all=true. "
                    + "둘 중 하나만 보내야 하고, 아무것도 안 보내거나 둘 다 보내면 400이다. "
                    + "어느 쪽이든 각 기록에 딸린 답례 알림이 함께 삭제되고, 실제로 지워진 건수를 돌려주므로 "
                    + "\"기록 3건을 삭제했어요\" 같은 안내에 그대로 쓰면 된다. "
                    + "ids 방식에서 이미 지워졌거나 다른 사용자의 ID는 오류 없이 건너뛴다. "
                    + "**보낸 사람(Person)과 카테고리는 남는다** — 사람까지 지우려면 DELETE /api/people, "
                    + "계정까지 지우려면 DELETE /api/users(회원탈퇴)를 쓴다. all=true는 되돌릴 수 없으니 "
                    + "화면에서 한 번 확인받고 호출할 것."
    )
    @DeleteMapping
    public ApiResponse<GiftRecordDeleteResponse> deleteAll(
            @Parameter(description = "삭제할 기록 ID 목록 (예: ?ids=1,2,3)")
            @RequestParam(required = false) List<Long> ids,
            @Parameter(description = "true면 내 마음 기록 전체를 삭제한다. ids와 함께 보낼 수 없다", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            if (ids != null && !ids.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "ids와 all=true는 함께 보낼 수 없습니다.");
            }
            return ApiResponse.success(giftRecordService.deleteAllOfUser());
        }
        return ApiResponse.success(giftRecordService.deleteAll(ids));
    }
}
