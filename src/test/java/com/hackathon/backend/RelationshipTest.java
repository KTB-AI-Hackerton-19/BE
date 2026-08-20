package com.hackathon.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.backend.domain.Relationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기본 관계 9종의 값 매칭 규칙.
 *
 * <p>관계는 사용자가 직접 추가할 수 있게 되면서(POST /api/relationships) <b>근사 매칭을 없앴다.</b>
 * 예전에는 "대학 동기"를 학교 동창으로 맞춰줬는데, 그러면 누가 "동호회"를 따로 등록해도 저장할 때마다
 * "친구"로 바뀌어 버린다. 목록에 없는 값은 맞춰주는 게 아니라 추가하면 된다.</p>
 *
 * <p>커스텀 관계까지 포함한 판정은 사용자별 목록이 필요해서 {@code RelationshipService.normalize}가 한다.
 * 여기서는 사용자와 무관한 기본 9종 매칭만 본다.</p>
 */
class RelationshipTest {

    @Test
    @DisplayName("한글 라벨과 enum 이름을 그대로 받는다")
    void exactValues() {
        assertThat(Relationship.exactMatch("친구")).isEqualTo(Relationship.FRIEND);
        assertThat(Relationship.exactMatch("FRIEND")).isEqualTo(Relationship.FRIEND);
        assertThat(Relationship.exactMatch("family")).isEqualTo(Relationship.FAMILY);
        assertThat(Relationship.exactMatch("  친구  ")).isEqualTo(Relationship.FRIEND);
        assertThat(Relationship.exactMatch("연인·배우자")).isEqualTo(Relationship.PARTNER);
    }

    @Test
    @DisplayName("자유 텍스트는 비슷한 값으로 맞춰주지 않는다 — 목록에 없으면 미지정")
    void noApproximation() {
        assertThat(Relationship.exactMatch("대학 동기")).isNull();
        assertThat(Relationship.exactMatch("회사 팀장")).isNull();
        assertThat(Relationship.exactMatch("사촌 동생")).isNull();
        // "동호회"는 FRIEND의 별칭이었지만, 이제는 사용자가 등록해서 쓰는 별개의 값이다.
        assertThat(Relationship.exactMatch("동호회")).isNull();
    }

    @Test
    @DisplayName("빈 값과 못 알아듣는 값은 기타가 아니라 미지정(null)이다")
    void unknownIsNull() {
        assertThat(Relationship.exactMatch("asdfqwer")).isNull();
        assertThat(Relationship.exactMatch("  ")).isNull();
        assertThat(Relationship.exactMatch(null)).isNull();
    }

    @Test
    @DisplayName("표시용 라벨 — 옛 enum 이름은 라벨로 바꾸고, 커스텀 관계는 그대로 통과시킨다")
    void displayLabel() {
        assertThat(Relationship.displayLabel("FRIEND")).isEqualTo("친구");   // enum으로 저장하던 시절의 행
        assertThat(Relationship.displayLabel("친구")).isEqualTo("친구");
        assertThat(Relationship.displayLabel("동호회")).isEqualTo("동호회");  // 사용자가 추가한 관계
        assertThat(Relationship.displayLabel("  러닝 크루 ")).isEqualTo("러닝 크루");
        assertThat(Relationship.displayLabel(null)).isNull();
        assertThat(Relationship.displayLabel("  ")).isNull();
    }
}
