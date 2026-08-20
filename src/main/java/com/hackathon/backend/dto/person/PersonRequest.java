package com.hackathon.backend.dto.person;

import com.hackathon.backend.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 사람 등록/수정 요청.
 *
 * <p>이름·성별·관계는 <b>등록(POST)에서만</b> 필수다. 수정(PATCH)은 부분 수정이라
 * 컨트롤러에 {@code @Valid}를 붙이지 않았고, 보내지 않은 필드는 기존 값이 유지된다.</p>
 */
@Schema(description = "사람 등록/수정 요청 (이름·성별·관계는 등록 시 필수)")
public record PersonRequest(
        @Schema(description = "이름", example = "김민수")
        @NotBlank(message = "이름을 입력해주세요.") String name,

        @Schema(description = "관계 — <b>등록 시 필수.</b> 자유 입력이 아니라 GET /api/relationships 의 value 중 하나여야 한다. "
                + "기본 관계는 한글 라벨(친구)/enum 이름(FRIEND) 모두 허용하고, 내가 추가한 관계는 그 이름 그대로 보내면 된다. "
                + "목록에 없는 값을 보내면 400 — 없는 관계는 POST /api/relationships로 먼저 추가한다",
                example = "친구")
        @NotBlank(message = "관계를 선택해주세요.") String relation,

        @Schema(description = "성별 — <b>등록 시 필수.</b> 한글 라벨(남성/여성/기타) 또는 enum 이름(MALE/FEMALE/OTHER) 모두 허용. "
                + "선물 추천의 참고 정보로 쓰인다", example = "남성")
        @NotNull(message = "성별을 선택해주세요.") Gender gender,

        @Schema(description = "생일 — 홈 화면 '마음 에이전트가 발견했어요' 카드의 다가오는 기념일 계산에 사용", example = "1998-09-18")
        LocalDate birthday,

        @Schema(description = "메모 (취향/기피 품목 등). 선물 추천의 참고 정보로 쓰인다", example = "커피를 좋아함") String memo
) {
}
