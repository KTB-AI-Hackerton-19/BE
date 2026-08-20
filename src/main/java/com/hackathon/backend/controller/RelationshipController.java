package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.relationship.RelationshipRequest;
import com.hackathon.backend.dto.relationship.RelationshipResponse;
import com.hackathon.backend.service.RelationshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관계", description = "사람 등록/기록 모달의 '관계' 드롭다운 선택지. 기본 9종 + 사용자가 추가한 관계")
@RestController
@RequestMapping("/api/relationships")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @Operation(
            summary = "관계 목록 조회",
            description = "사람 등록/수정 폼과 기록 모달의 '관계' select를 이 목록으로 그린다. 자유 입력은 받지 않는다. "
                    + "<b>기본 9종이 먼저, 내가 추가한 관계가 뒤</b>로 붙어 나온다 "
                    + "(내가 추가한 항목은 custom=true, code=\"CUSTOM\"). "
                    + "응답의 value를 그대로 PersonRequest.relation / GiftRecordCreateRequest.relation 에 넣으면 된다."
    )
    @GetMapping
    public ApiResponse<List<RelationshipResponse>> list() {
        return ApiResponse.success(relationshipService.list());
    }

    @Operation(
            summary = "관계 추가",
            description = "드롭다운에 없는 관계를 그 자리에서 추가한다. 추가 즉시 목록 API에 반영되며 서버 재시작이 필요 없다. "
                    + "추가한 관계는 나를 위한 것이라 다른 사용자에게는 보이지 않는다. "
                    + "기본 9종과 같은 이름이거나 이미 추가한 이름이면 409."
    )
    @PostMapping
    public ApiResponse<RelationshipResponse> create(@Valid @RequestBody RelationshipRequest request) {
        return ApiResponse.success(relationshipService.create(request));
    }
}
