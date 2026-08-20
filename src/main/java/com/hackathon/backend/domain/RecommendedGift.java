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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RecommendedGift(User user, Person person, String emoji, String name, Integer amount,
                           RecommendationTag tag, String reason, String productUrl, String imageUrl,
                           String thankYouMessage, Integer displayOrder, RecommendationSlot slot) {
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
        this.createdAt = LocalDateTime.now();
    }

    /** 미리 받아둔 세트를 화면에 보이는 세트로 올린다. */
    public void promote() {
        this.slot = RecommendationSlot.CURRENT;
    }
}
