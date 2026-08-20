package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** AI가 생성한 선물 추천 후보. 홈 화면 "이런 선물은 어때요?" 카드 한 장에 대응. */
@Entity
@Table(name = "recommended_gifts", indexes = {
        @Index(name = "idx_recommendation_user_person", columnList = "user_id, person_id, slot")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendedGift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 누구를 위한 추천인지. 대상이 아직 없으면(=기록이 하나도 없으면) null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(nullable = false, length = 200)
    private String name;

    /** 추천 선물의 예상 금액(원) */
    @Column
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationTag tag;

    @Column(length = 500)
    private String reason;

    /** 실제 상품 구매 링크. AI가 상품을 못 찾으면(자주 그렇다) null이다. */
    @Column(length = 1000)
    private String productUrl;

    /**
     * 상품 대표 이미지 주소. {@code productUrl} 페이지의 og:image를 읽어 채운다
     * ({@code ProductImageResolver}).
     *
     * <p>매번 다시 긁지 않고 추천과 함께 저장하는 이유는, 추천이 캐시되어 여러 번 조회되는데
     * 이미지를 조회 시점마다 뽑으면 쇼핑몰을 그만큼 두드리게 되고 화면도 그만큼 느려지기 때문이다.
     * 추천이 새로 생성될 때만 새로 뽑으면 된다.</p>
     */
    @Column(length = 1000)
    private String imageUrl;

    /**
     * AI가 써준 답례 인사 문구.
     *
     * <p>AI는 추천 한 세트에 문구 하나를 주므로 같은 세트의 카드가 모두 같은 값을 갖는다.
     * 별도 테이블로 빼지 않은 이유는, 추천이 통째로 생성·삭제되어 수명이 정확히 같기 때문이다
     * (따로 두면 지울 때 짝을 맞춰야 하고 고아 행이 생길 여지만 늘어난다).</p>
     */
    @Column(length = 1000)
    private String thankYouMessage;

    @Column(nullable = false)
    private Integer displayOrder;

    /** 지금 보여주는 세트인지, 미리 받아둔 다음 세트인지. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecommendationSlot slot;

    /**
     * AI가 아니라 더미 폴백으로 만들어진 세트인지.
     *
     * <p>추천은 캐시되므로, AI가 잠깐 죽었을 때 만들어진 더미를 그냥 저장해두면 그 뒤로는
     * "캐시가 차 있다"는 이유로 아무도 다시 만들지 않아 더미가 계속 화면에 남는다. 이 표시가 있으면
     * 화면에는 일단 그걸 보여주면서(빈 화면보다 낫다) 뒤에서 진짜 추천으로 조용히 갈아끼울 수 있다.</p>
     *
     * <p>컬럼명을 {@code fallback}이 아니라 {@code is_fallback}으로 둔 건 일부 DB에서 예약어와 부딪히는 걸 피하려는 것.</p>
     */
    @Column(name = "is_fallback")
    private boolean fallback;

    /**
     * 한 번에 생성된 세트를 묶는 식별자.
     *
     * <p>같은 슬롯에 세트가 두 벌 들어갈 수 있기 때문에 둔다. 미리받기(백그라운드)와 화면 요청이
     * 캐시가 빈 것을 동시에 보면 둘 다 AI를 부르고 둘 다 저장하는데, 이 표시가 없으면
     * {@code displayOrder}로만 정렬해서 <b>서로 다른 세트의 카드가 섞여</b> 나간다
     * (답례 문구가 카드마다 달라지는 식으로 화면에 그대로 드러난다).</p>
     *
     * <p>읽을 때 가장 최근 세트 하나만 고르고 나머지는 버리는 데 쓴다.</p>
     */
    @Column(length = 36)
    private String batchId;

    /**
     * 추천의 <b>근거가 바뀌어</b> 다시 만들어야 하는 세트인지.
     *
     * <p>근거가 바뀌었을 때(기록 등록·수정, 사람 정보 변경) 예전에는 캐시를 지웠는데, 그러면 다음 홈 진입이
     * AI 응답 시간(실측 8~9초)만큼 그대로 멈췄다. 지우는 대신 이 표시만 남기면 화면은 직전 세트를 즉시 받고,
     * 새 세트는 백그라운드에서 만들어져 다음 진입 때 바뀐다.</p>
     *
     * <p>낡은 값이 잠깐 보이는 걸 감수하는 이유는, 그 창이 <b>스스로 닫히기</b> 때문이다.
     * (예전에 캐시를 지우도록 바꾼 건 낡은 추천이 <b>영원히</b> 남는 걸 막으려던 것이었다.)</p>
     */
    @Column(name = "is_stale")
    private boolean stale;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RecommendedGift(User user, Person person, String emoji, String name, Integer amount,
                           RecommendationTag tag, String reason, String productUrl, String imageUrl,
                           String thankYouMessage, Integer displayOrder, RecommendationSlot slot,
                           boolean fallback, String batchId) {
        this.user = user;
        this.person = person;
        this.emoji = emoji;
        this.name = name;
        this.amount = amount;
        this.tag = tag;
        this.reason = reason;
        this.productUrl = productUrl;
        this.imageUrl = imageUrl;
        this.thankYouMessage = thankYouMessage;
        this.displayOrder = displayOrder;
        this.slot = slot;
        this.fallback = fallback;
        this.batchId = batchId;
        this.createdAt = LocalDateTime.now();
    }

    /** 미리 받아둔 세트를 화면에 보이는 세트로 올린다. */
    public void promote() {
        this.slot = RecommendationSlot.CURRENT;
    }
}
