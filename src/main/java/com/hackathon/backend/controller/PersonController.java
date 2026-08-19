package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.gift.GiftRecordResponse;
import com.hackathon.backend.dto.person.PersonDetailResponse;
import com.hackathon.backend.dto.person.PersonRequest;
import com.hackathon.backend.dto.person.PersonResponse;
import com.hackathon.backend.service.GiftRecordService;
import com.hackathon.backend.service.PersonService;
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

    public PersonController(PersonService personService, GiftRecordService giftRecordService) {
        this.personService = personService;
        this.giftRecordService = giftRecordService;
    }

    @Operation(
            summary = "사람 목록 조회",
            description = "사람들 화면의 목록. 각 항목에 마음 개수(giftCount), 최근 받은 선물(latestGift), "
                    + "가장 가까운 답례 알림일(upcomingReminderDate)까지 채워서 내려주므로 추가 호출이 필요 없다. "
                    + "q를 주면 이름으로 검색한다."
    )
    @GetMapping
    public ApiResponse<List<PersonResponse>> list(
            @Parameter(description = "이름 검색어 (부분 일치)", example = "민수") @RequestParam(required = false) String q) {
        return ApiResponse.success(personService.list(q));
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
    public ApiResponse<List<GiftRecordResponse>> records(
            @Parameter(description = "조회할 사람 ID") @PathVariable Long id) {
        personService.get(id); // 소유권 검증 (다른 사용자의 사람이면 404)
        return ApiResponse.success(giftRecordService.listByPerson(id));
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
            summary = "사람 삭제",
            description = "남아있는 마음 기록이 없을 때만 삭제된다. 기록이 남아 있으면 400과 함께 남은 건수를 안내한다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(description = "삭제할 사람 ID") @PathVariable Long id) {
        personService.delete(id);
        return ApiResponse.success(null);
    }
}
