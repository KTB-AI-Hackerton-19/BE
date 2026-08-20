package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.category.CategoryRequest;
import com.hackathon.backend.dto.category.CategoryUpdateRequest;
import com.hackathon.backend.dto.category.CategoryResponse;
import com.hackathon.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카테고리", description = "선물 카테고리 마스터. 코드가 아니라 DB row로 관리하므로 재배포 없이 추가/수정할 수 있다")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(
            summary = "카테고리 목록 조회",
            description = "마음 기록 화면의 카테고리 필터 칩('전체'는 프론트가 앞에 붙이면 됨)과 기록 모달의 카테고리 select를 "
                    + "이 목록으로 그린다. 하드코딩하지 말고 이 API를 쓰면 카테고리를 추가해도 프론트 수정이 필요 없다. "
                    + "각 항목의 recordCount는 현재 로그인 사용자의 해당 카테고리 기록 수라, 0인 칩은 숨기는 식으로 활용 가능. "
                    + "이 목록은 선물(GIFT) 전용이다 — 경조사 유형은 고정 7종이라 GET /api/gift-records/event-categories 참고."
    )
    @GetMapping
    public ApiResponse<List<CategoryResponse>> list(
            @Parameter(description = "비활성(active=false) 카테고리까지 포함할지 여부. 기본 false")
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(categoryService.list(includeInactive));
    }

    @Operation(
            summary = "카테고리 추가",
            description = "새 카테고리를 추가한다. 추가 즉시 목록 API와 기록 등록/수정 API에 반영되며 서버 재시작이 필요 없다. "
                    + "emoji/color/displayOrder를 생략하면 각각 🎁 / blue / 맨 뒤로 채워진다. 선물 카테고리 전용이다."
    )
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(categoryService.create(request));
    }

    @Operation(
            summary = "카테고리 수정",
            description = "이름/이모지/색상/정렬순서/노출여부를 수정한다. active=false로 바꾸면 목록에서 숨겨지지만, "
                    + "이미 이 카테고리로 저장된 기록은 그대로 유지된다."
    )
    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> update(
            @Parameter(description = "수정할 카테고리 ID") @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.update(id, request));
    }

    @Operation(
            summary = "카테고리 삭제",
            description = "내 카테고리만 삭제된다. 그 카테고리로 저장된 마음 기록은 지워지지 않고 '기타'로 옮겨지며, "
                    + "옮겨진 건수를 응답으로 돌려준다. '기타' 자체는 폴백 대상이라 삭제할 수 없다. "
                    + "잠시 목록에서만 숨기고 싶다면 PATCH로 active:false를 주면 된다."
    )
    @DeleteMapping("/{id}")
    public ApiResponse<Integer> delete(
            @Parameter(description = "삭제할 카테고리 ID") @PathVariable Long id) {
        return ApiResponse.success(categoryService.delete(id));
    }
}
