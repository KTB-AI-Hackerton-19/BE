package com.hackathon.backend.dto.person;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사람 삭제 결과. 사람 한 명을 지우면 그 사람의 기록·답례 알림·선물 추천이 함께 사라지므로,
 * 몇 건이 딸려 지워졌는지 돌려준다. 프론트는 이 값으로 "김민수님과 기록 3건을 삭제했어요" 같은
 * 안내를 띄우면 된다.
 */
@Schema(description = "사람 삭제 결과")
public record PersonDeleteResponse(
        @Schema(description = "실제로 삭제된 사람 수. 없는 id나 다른 사용자의 사람은 세지 않는다", example = "2")
        int deletedPeople,

        @Schema(description = "함께 삭제된 마음 기록 수", example = "5")
        int deletedRecords,

        @Schema(description = "함께 삭제된 답례 알림 수", example = "3")
        int deletedReminders
) {
    public static PersonDeleteResponse empty() {
        return new PersonDeleteResponse(0, 0, 0);
    }
}
