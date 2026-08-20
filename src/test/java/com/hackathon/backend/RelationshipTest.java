package com.hackathon.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.backend.domain.Relationship;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관계 자유 텍스트 → 카테고리 매칭. AI가 뱉는 값과 구버전 데이터가 여기로 들어온다. */
class RelationshipTest {

    @Test
    @DisplayName("한글 라벨과 enum 이름을 그대로 받는다")
    void exactValues() {
        assertThat(Relationship.from("친구")).isEqualTo(Relationship.FRIEND);
        assertThat(Relationship.from("FRIEND")).isEqualTo(Relationship.FRIEND);
        assertThat(Relationship.from("family")).isEqualTo(Relationship.FAMILY);
    }

    @Test
    @DisplayName("자유 텍스트는 키워드로 가장 가까운 카테고리에 붙인다")
    void freeText() {
        assertThat(Relationship.from("대학 동기")).isEqualTo(Relationship.SCHOOL);
        assertThat(Relationship.from("회사 팀장")).isEqualTo(Relationship.WORK);
        assertThat(Relationship.from("동호회 친구")).isEqualTo(Relationship.FRIEND);
    }

    @Test
    @DisplayName("키워드가 여럿 걸리면 앞에서 걸린 쪽이 이긴다 — 뒤는 부연 설명이다")
    void earliestKeywordWins() {
        assertThat(Relationship.from("사촌 동생")).isEqualTo(Relationship.RELATIVE);   // 동생(가족) 아님
        assertThat(Relationship.from("남자친구")).isEqualTo(Relationship.PARTNER);      // 친구 아님
        assertThat(Relationship.from("고등학교 친구")).isEqualTo(Relationship.SCHOOL);
    }

    @Test
    @DisplayName("못 알아듣는 값은 기타가 아니라 미지정(null)이다")
    void unknownIsNull() {
        assertThat(Relationship.from("asdfqwer")).isNull();
        assertThat(Relationship.from("  ")).isNull();
        assertThat(Relationship.from(null)).isNull();
    }
}
