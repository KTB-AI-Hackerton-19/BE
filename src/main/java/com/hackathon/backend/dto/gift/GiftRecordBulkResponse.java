package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 벌크 저장/확정 결과. {@code savedCount}는 항상 {@code records.size()}와 같다 —
 * 부분 저장이 없어서 "몇 건 중 몇 건"을 따질 일이 없기 때문이다.
 * 실패하면 아무것도 저장되지 않고 400이 나간다.
 */
@Schema(description = "벌크 저장 결과. 전부 성공했을 때만 내려간다")
public record GiftRecordBulkResponse(
        @Schema(description = "저장된 건수. \"3명의 마음을 기록했어요\" 문구에 그대로 쓰면 된다", example = "3")
        int savedCount,

        @Schema(description = "저장된 기록들. 보낸 순서를 그대로 유지한다")
        List<GiftRecordResponse> records
) {
}
