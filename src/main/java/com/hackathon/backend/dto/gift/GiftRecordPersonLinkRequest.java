package com.hackathon.backend.dto.gift;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/gift-records/{id}/person} 요청 — 사람 없이 리스트에만 있던 기록을 사람(Person)에 연결한다.
 *
 * <p>경조사는 기본이 "매핑 안 함"이다. 하객 전원을 사람들 목록에 올리면 목록이 못 쓰게 되기 때문이다.
 * 그래서 <b>사용자가 직접 고른 사람만</b> 이 API로 뒤늦게 연결한다.</p>
 */
@Schema(description = "기록의 보낸 사람을 Person에 연결하는 요청")
public record GiftRecordPersonLinkRequest(

        @Schema(description = "이미 등록된 사람에 연결할 때의 Person ID. 생략하면 기록에 적힌 이름으로 새로 등록한다",
                example = "3")
        Long personId,

        @Schema(description = "연결하면서 지정할 관계 (선택). GET /api/relationships 참고", example = "친구")
        String relation,

        @Schema(description = "true면 같은 이름으로 남아 있는 다른 사람 미등록 기록까지 한 번에 같은 사람으로 묶는다. "
                + "한 결혼식에서 같은 사람이 여러 번 잡힌 경우에 쓴다", example = "false")
        Boolean applySameName
) {
}
