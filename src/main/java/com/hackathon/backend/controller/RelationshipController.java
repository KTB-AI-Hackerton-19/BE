package com.hackathon.backend.controller;

import com.hackathon.backend.domain.Relationship;
import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.relationship.RelationshipResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관계", description = "사람 등록/기록 모달의 '관계' 드롭다운 선택지")
@RestController
@RequestMapping("/api/relationships")
public class RelationshipController {

    @Operation(
            summary = "관계 카테고리 목록 조회",
            description = "사람 등록/수정 폼과 기록 모달의 '관계' select를 이 목록으로 그린다. 자유 입력은 받지 않는다. "
                    + "응답의 value를 그대로 PersonRequest.relation / GiftRecordCreateRequest.relation 에 넣으면 된다. "
                    + "항목이 늘어도 이 API를 쓰면 프론트 수정이 필요 없다."
    )
    @GetMapping
    public ApiResponse<List<RelationshipResponse>> list() {
        return ApiResponse.success(Arrays.stream(Relationship.values())
                .map(RelationshipResponse::of)
                .toList());
    }
}
