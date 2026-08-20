package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마음 기록 삭제 결과. 기록을 지우면 거기 딸린 답례 알림도 함께 사라지므로 각각 몇 건인지 돌려준다.
 * 프론트는 "기록 3건을 삭제했어요" 같은 안내에 그대로 쓰면 된다.
 *
 * <p>사람은 지우지 않는다 — 기록이 없어졌다고 상대방을 목록에서 없앨 이유가 없다
 * (사람까지 지우려면 {@code DELETE /api/people}을 쓴다).</p>
 */
@Schema(description = "마음 기록 삭제 결과")
public record GiftRecordDeleteResponse(
        @Schema(description = "실제로 삭제된 기록 수. 없는 id나 다른 사용자의 기록은 세지 않는다", example = "3")
        int deletedRecords,

        @Schema(description = "함께 삭제된 답례 알림 수", example = "2")
        int deletedReminders
) {
    public static GiftRecordDeleteResponse empty() {
        return new GiftRecordDeleteResponse(0, 0);
    }
}
