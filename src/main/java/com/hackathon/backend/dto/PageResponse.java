package com.hackathon.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "페이징 목록 공통 응답")
public record PageResponse<T>(
        @Schema(description = "현재 페이지의 항목 목록") List<T> content,
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "필터 조건에 맞는 전체 항목 수 (화면의 'N개의 마음'에 그대로 사용)", example = "4") long totalElements,
        @Schema(description = "전체 페이지 수", example = "1") int totalPages,
        @Schema(description = "마지막 페이지 여부", example = "true") boolean last
) {
    public static <E, T> PageResponse<T> of(Page<E> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
