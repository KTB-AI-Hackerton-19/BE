package com.hackathon.backend.domain;

/**
 * 추천 한 세트가 놓이는 자리.
 *
 * <p>화면에 지금 보이는 세트({@link #CURRENT})와, "다시 추천받기"를 누를 때 즉시 갈아끼우려고
 * 백그라운드에서 미리 받아둔 세트({@link #NEXT})를 구분한다. 버튼을 누른 시점에 AI를 부르면
 * 몇 초씩 멈춰 보이므로, 화면을 그리는 동안 다음 세트를 미리 만들어 대기시킨다.</p>
 */
public enum RecommendationSlot {

    /** 지금 화면에 보이는 세트. */
    CURRENT,

    /** '다시 추천받기'에 대비해 미리 만들어 둔 다음 세트. */
    NEXT
}
