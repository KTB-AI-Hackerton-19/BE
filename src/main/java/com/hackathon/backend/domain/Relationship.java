package com.hackathon.backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 사람과 나의 관계. 자유 입력이 아니라 <b>고정된 카테고리 중 하나</b>를 고른다.
 *
 * <p>자유 입력이면 "회사 동료"/"직장동료"/"회사동료"가 전부 다른 값이 되어 관계별 집계·필터가 불가능하고,
 * 선물 추천에 넘기는 값도 매번 달라진다. 그래서 화면은 드롭다운(=이 목록)으로만 고르게 한다.
 * 목록이 9개로 작고 잘 바뀌지 않아 Category처럼 테이블로 빼지 않고 enum으로 뒀다
 * (항목을 늘리려면 여기 상수만 추가하면 되고, 화면은 {@code GET /api/relationships}로 받아 그리므로 프론트 수정은 필요 없다).</p>
 *
 * <p>JSON으로는 한글 라벨("친구")로 오가며, 읽을 때는 enum 이름("FRIEND")과
 * AI/구버전이 보내는 자유 텍스트("대학 동기")도 받아 가장 가까운 항목으로 맞춰준다.</p>
 */
@Schema(description = "관계 카테고리. 값은 한글 라벨로 주고받는다")
public enum Relationship {

    FAMILY("가족", List.of("가족", "부모", "어머니", "아버지", "엄마", "아빠", "형", "누나", "언니", "오빠", "동생",
            "아들", "딸", "자녀", "며느리", "사위", "장인", "장모", "시부모")),
    RELATIVE("친척", List.of("친척", "사촌", "이모", "고모", "삼촌", "외삼촌", "조카", "숙모", "큰아버지", "작은아버지",
            "할머니", "할아버지", "친지")),
    PARTNER("연인·배우자", List.of("연인", "배우자", "남편", "아내", "와이프", "애인", "여자친구", "남자친구", "여친", "남친")),
    FRIEND("친구", List.of("친구", "절친", "베프", "동네 친구", "동호회", "모임")),
    SCHOOL("학교 동창", List.of("동창", "동기", "선배", "후배", "학교", "학과", "대학", "고등학교", "중학교", "초등학교",
            "동아리", "은사", "스승", "선생님")),
    WORK("직장 동료", List.of("직장", "회사", "동료", "상사", "팀장", "부장", "과장", "대리", "사수", "부사수", "직원",
            "인턴", "알바", "업무")),
    NEIGHBOR("이웃", List.of("이웃", "옆집", "윗집", "아랫집", "동네")),
    BUSINESS("거래처", List.of("거래처", "고객", "클라이언트", "협력사", "파트너사", "업체", "비즈니스")),
    OTHER("기타", List.of("기타", "지인", "그 외"));

    private final String label;
    private final List<String> aliases;

    Relationship(String label, List<String> aliases) {
        this.label = label;
        this.aliases = aliases;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /**
     * 화면이 보내는 한글 라벨("친구"), enum 이름("FRIEND"), 그리고 AI가 뱉는 자유 텍스트("대학 동기")를 모두 받는다.
     *
     * <p>어느 쪽에도 안 걸리는 값은 {@link #OTHER}가 아니라 null(미지정)로 둔다 —
     * 값이 있는데 못 알아들은 것과, 사용자가 정말 "기타"를 고른 것은 화면에서 구분돼야 하기 때문이다.
     * (미지정이면 사람 카드에 "관계 미정"으로 뜬다)</p>
     */
    @JsonCreator
    public static Relationship from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (Relationship relationship : values()) {
            if (relationship.label.equals(trimmed) || relationship.name().equalsIgnoreCase(trimmed)) {
                return relationship;
            }
        }
        // 정확히 안 맞으면 키워드로 근사시킨다. "대학 동기" → 학교 동창, "회사 팀장" → 직장 동료
        //
        // 여러 키워드가 걸리면 문자열 앞쪽에서 걸린 쪽을 택한다. 관계 표현은 앞이 본체이고 뒤가 부연이기
        // 때문이다 — "사촌 동생"은 동생(가족)이 아니라 사촌(친척)이고, "남자친구"는 친구가 아니라 연인이다.
        // 위치가 같으면 더 긴 키워드가 더 구체적이므로 그쪽을 택한다.
        Relationship best = null;
        int bestIndex = Integer.MAX_VALUE;
        int bestLength = 0;
        for (Relationship relationship : values()) {
            for (String alias : relationship.aliases) {
                int index = trimmed.indexOf(alias);
                if (index < 0) {
                    continue;
                }
                if (index < bestIndex || (index == bestIndex && alias.length() > bestLength)) {
                    best = relationship;
                    bestIndex = index;
                    bestLength = alias.length();
                }
            }
        }
        return best;
    }

    /** null이면 null을 그대로 돌려주는 라벨 변환 (AI 요청 등 문자열이 필요한 곳에서 쓴다). */
    public static String labelOf(Relationship relationship) {
        return relationship == null ? null : relationship.label;
    }
}
