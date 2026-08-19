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
        @Index(name = "idx_recommendation_user_person", columnList = "user_id, person_id")
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

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RecommendedGift(User user, Person person, String emoji, String name, Integer amount,
                           RecommendationTag tag, String reason, Integer displayOrder) {
        this.user = user;
        this.person = person;
        this.emoji = emoji;
        this.name = name;
        this.amount = amount;
        this.tag = tag;
        this.reason = reason;
        this.displayOrder = displayOrder;
        this.createdAt = LocalDateTime.now();
    }
}
