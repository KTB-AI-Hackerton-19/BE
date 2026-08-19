package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.search.SearchResponse;
import com.hackathon.backend.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "통합 검색", description = "상단바 '사람이나 선물을 검색해보세요' 검색창")
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(
            summary = "사람·선물 통합 검색",
            description = "검색어 하나로 사람(이름 부분 일치)과 마음 기록(선물명/받은 이유/보낸 사람 이름 부분 일치)을 동시에 찾는다. "
                    + "people와 records를 섹션으로 나눠 드롭다운에 그리면 된다. 검색어가 비어 있으면 빈 결과를 반환한다."
    )
    @GetMapping
    public ApiResponse<SearchResponse> search(
            @Parameter(description = "검색어", example = "민수") @RequestParam(required = false) String q,
            @Parameter(description = "각 섹션당 최대 개수 (기본 10, 최대 50)", example = "10")
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(searchService.search(q, limit));
    }
}
