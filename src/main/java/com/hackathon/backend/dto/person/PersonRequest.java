package com.hackathon.backend.dto.person;

import com.hackathon.backend.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

@Schema(description = "사람 등록/수정 요청")
public record PersonRequest(
        @Schema(description = "이름", example = "김민수")
        @NotBlank(message = "이름을 입력해주세요.") String name,

        @Schema(description = "관계 (예: 친한 친구, 직장 동료, 대학 동기)", example = "친한 친구") String relation,

        @Schema(description = "성별 — 선택 항목. 한글 라벨(남성/여성/기타) 또는 enum 이름(MALE/FEMALE/OTHER) 모두 허용. "
                + "선물 추천의 참고 정보로 쓰인다", example = "남성")
        Gender gender,

        @Schema(description = "생일 — 홈 화면 '마음 에이전트가 발견했어요' 카드의 다가오는 기념일 계산에 사용", example = "1998-09-18")
        LocalDate birthday,

        @Schema(description = "메모 (취향/기피 품목 등). 선물 추천의 참고 정보로 쓰인다", example = "커피를 좋아함") String memo
) {
}
