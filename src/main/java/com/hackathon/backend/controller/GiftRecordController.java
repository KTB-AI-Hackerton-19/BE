package com.hackathon.backend.controller;

import com.hackathon.backend.domain.GiftRecordStatus;
import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.PageResponse;
import com.hackathon.backend.dto.gift.GiftRecordCreateRequest;
import com.hackathon.backend.dto.gift.GiftRecordDeleteResponse;
import com.hackathon.backend.dto.gift.GiftRecordExtractRequest;
import com.hackathon.backend.dto.gift.GiftRecordExtractResponse;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkRequest;
import com.hackathon.backend.dto.gift.GiftRecordPersonLinkResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.gift.GiftRecordThankedRequest;
import com.hackathon.backend.dto.gift.GiftRecordUpdateRequest;
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

    public GiftRecordController(GiftRecordService giftRecordService) {
        this.giftRecordService = giftRecordService;
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
            @Parameter(description = "분류 필터. EVENT(경조사 전체) / GIFT(선물) / CELEBRATION(경사) / CONDOLENCE(조사). "
                    + "한글(경조사·선물·경사·조사)도 허용. 생략하면 전체", example = "EVENT")
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
            summary = "AI 이미지 분석 (DRAFT 생성, 여러 명 지원)",
            description = "기록 모달에서 사진을 올린 뒤 호출한다. presigned URL로 S3에 업로드된 imageKey를 받아 "
                    + "백엔드가 조회용 presigned GET URL을 만들어 AI 서비스에 imageUrl로 전달하고, 결과를 status=DRAFT로 저장한다.\n\n"
                    + "**사진에서 여러 명이 나오면 사람 수만큼 DRAFT가 만들어져 records에 전부 담긴다.** "
                    + "personCount가 사람 수, multiple이 2명 이상 여부다. 1명이면 records 원소가 1개이고, "
                    + "첫 번째 기록의 필드(person/date/occasion/gift/category/price ...)는 예전처럼 응답 최상위에도 그대로 있다.\n\n"
                    + "AI 값이 경조사(결혼식·장례식 등)로 판정되면 그 사진의 사람 전원이 경조사 탭의 같은 이벤트로 묶이고, "
                    + "그 이벤트가 eventCategory로 함께 내려간다 — 같은 이름의 이벤트가 이미 있으면 새로 만들지 않고 재사용하며 "
                    + "(eventCategoryCreated=false), 새로 만들었으면 true다.\n\n"
                    + "AI 서비스가 아직 없으면(AI_SERVICE_URL 미설정) 하드코딩된 더미 결과 1건을 반환하므로 지금도 전체 흐름을 테스트할 수 있다."
    )
    @PostMapping("/extract")
    public ApiResponse<GiftRecordExtractResponse> extract(@Valid @RequestBody GiftRecordExtractRequest request) {
        return ApiResponse.success(giftRecordService.extract(request));
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
            summary = "마음 기록 삭제 (다중)",
            description = "목록에서 여러 건을 체크해 한 번에 지울 때 사용한다. 각 기록에 딸린 답례 알림도 함께 삭제된다. "
                    + "이미 지워졌거나 다른 사용자의 ID는 오류 없이 건너뛰고, 실제로 지워진 건수만 돌려주므로 "
                    + "\"기록 3건을 삭제했어요\" 같은 안내에 그대로 쓰면 된다. ID를 하나도 보내지 않으면 400. "
                    + "보낸 사람(Person)은 지우지 않는다 — 사람까지 지우려면 DELETE /api/people을 쓴다."
    )
    @DeleteMapping
    public ApiResponse<GiftRecordDeleteResponse> deleteAll(
            @Parameter(description = "삭제할 기록 ID 목록 (예: ?ids=1,2,3)") @RequestParam List<Long> ids) {
        return ApiResponse.success(giftRecordService.deleteAll(ids));
    }
}
