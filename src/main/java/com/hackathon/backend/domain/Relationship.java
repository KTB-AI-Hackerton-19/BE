package com.hackathon.backend.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <b>기본 관계 9종.</b> 모든 사용자가 공통으로 갖는 붙박이 목록이라 테이블이 아니라 코드에 둔다.
 *
 * <p>여기 없는 관계는 사용자가 {@code POST /api/relationships}로 직접 추가하고
 * ({@link CustomRelationship}), 화면의 드롭다운은 <b>기본 9종 + 그 사용자가 추가한 것</b>을
 * {@code GET /api/relationships}로 한 번에 받아 그린다. 즉 저장되는 값은 언제나
 * <b>이미 목록에 등록돼 있는 값</b>이다 — 자유 입력도, 비슷한 값으로 맞춰주는 추측도 없다.</p>
 *
 * <p>저장·전송되는 값은 enum 이름이 아니라 <b>한글 라벨</b>("친구")이다. 커스텀 관계는 enum이 될 수 없어
 * 어차피 문자열로 저장해야 하는데, 기본은 enum 이름("FRIEND") 커스텀은 라벨이면 같은 컬럼에 두 체계가
 * 섞이기 때문이다.</p>
 */
@Schema(description = "기본 관계 카테고리. 값은 한글 라벨로 주고받는다")
public enum Relationship {

    FAMILY("가족"),
    RELATIVE("친척"),
    PARTNER("연인·배우자"),
    FRIEND("친구"),
    SCHOOL("학교 동창"),
    WORK("직장 동료"),
    NEIGHBOR("이웃"),
    BUSINESS("거래처"),
    OTHER("기타");

    private final String label;

    Relationship(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 한글 라벨("친구") 또는 enum 이름("FRIEND")이 <b>정확히</b> 일치하는 기본 관계. 없으면 null.
     *
     * <p>일부러 정확히 일치할 때만 걸리게 한다. 예전에는 "대학 동기"를 학교 동창으로 맞춰주는 근사 매칭이
     * 있었는데, 관계를 사용자가 직접 추가하는 구조에서는 해가 된다 — 누가 "동호회"를 따로 등록해도
     * 저장할 때마다 "친구"로 바뀌어 버린다. 목록에 없는 값은 맞춰주는 게 아니라 추가하면 된다.</p>
     */
    public static Relationship exactMatch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (Relationship relationship : values()) {
            if (relationship.label.equals(trimmed) || relationship.name().equalsIgnoreCase(trimmed)) {
                return relationship;
            }
        }
        return null;
    }

    /**
     * 저장돼 있던 값을 화면에 내보낼 라벨로 바꾼다.
     *
     * <p>커스텀 관계는 그대로 통과시키고, enum 이름으로 저장된 옛 데이터("FRIEND")만 라벨("친구")로 고쳐준다.
     * 관계를 enum 컬럼으로 저장하던 시절의 행이 남아 있어도 드롭다운 값과 어긋나지 않게 하기 위한 것이다.</p>
     */
    public static String displayLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Relationship exact = exactMatch(value);
        return exact != null ? exact.getLabel() : value.trim();
    }
}
