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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "받은 마음" 한 건. 프론트 디자인의 record 객체와 1:1로 대응한다.
 *
 * <pre>
 * { person, relation, date, reminderDate, occasion, gift, category, price, emoji, color, thanked }
 * </pre>
 *
 * <p>person/relation → {@link Person}, date → receivedDate, gift → giftName, price → amount(정수),
 * emoji/color → {@link Category}에서 파생, 나머지는 그대로 컬럼.</p>
 */
@Entity
@Table(name = "gift_records", indexes = {
        @Index(name = "idx_gift_user_received", columnList = "user_id, receivedDate"),
        @Index(name = "idx_gift_user_created", columnList = "user_id, createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GiftRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 보낸 사람. DRAFT(AI 추출 직후) 단계에서는 아직 미지정일 수 있음 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** S3 원본 이미지 key (응답에는 presigned GET URL로 변환해서 내려감) */
    @Column
    private String imageKey;

    /** AI가 이미지에서 읽어낸 보낸 사람 이름 (확정 전 참고용) */
    @Column
    private String extractedSenderName;

    /** AI가 추측한 관계 (확정 전 참고용) */
    @Column
    private String extractedRelationship;

    /** 받은 이유 - 자유 텍스트 (예: 내 생일, 프로젝트 축하, 결혼식) */
    @Column(length = 200)
    private String occasion;

    /** 선물명 (예: 스타벅스 케이크) */
    @Column(length = 200)
    private String giftName;

    /** 금액(원). 화면의 "35,000원"은 응답의 amountText로 별도 제공 */
    @Column
    private Integer amount;

    /** 받은 날짜 */
    @Column
    private LocalDate receivedDate;

    /** 답례 알림일. 값이 있으면 같은 날짜의 ReminderTask가 함께 유지된다 */
    @Column
    private LocalDate reminderDate;

    /** 감사/답례 완료 여부 — 화면의 "감사 완료" / "확인 필요" 뱃지 */
    @Column(nullable = false)
    private boolean thanked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftRecordStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static GiftRecord createConfirmed(User user, Person person, Category category, String occasion,
                                             String giftName, Integer amount, LocalDate receivedDate,
                                             LocalDate reminderDate, boolean thanked) {
        GiftRecord record = new GiftRecord();
        record.user = user;
        record.person = person;
        record.category = category;
        record.occasion = occasion;
        record.giftName = giftName;
        record.amount = amount;
        record.receivedDate = receivedDate;
        record.reminderDate = reminderDate;
        record.thanked = thanked;
        record.status = GiftRecordStatus.CONFIRMED;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    public static GiftRecord createDraft(User user, String imageKey, String extractedSenderName,
                                         String extractedRelationship, Category category, String occasion,
                                         String giftName, Integer amount, LocalDate receivedDate,
                                         LocalDate reminderDate) {
        GiftRecord record = new GiftRecord();
        record.user = user;
        record.imageKey = imageKey;
        record.extractedSenderName = extractedSenderName;
        record.extractedRelationship = extractedRelationship;
        record.category = category;
        record.occasion = occasion;
        record.giftName = giftName;
        record.amount = amount;
        record.receivedDate = receivedDate;
        record.reminderDate = reminderDate;
        record.thanked = false;
        record.status = GiftRecordStatus.DRAFT;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    /** 확인/수정 폼 저장 — null로 들어온 필드는 기존 값을 유지한다(부분 수정 PATCH 시맨틱). */
    public void applyUpdate(Person person, Category category, String occasion, String giftName,
                            Integer amount, LocalDate receivedDate, LocalDate reminderDate, Boolean thanked) {
        if (person != null) {
            this.person = person;
        }
        if (category != null) {
            this.category = category;
        }
        if (occasion != null) {
            this.occasion = occasion;
        }
        if (giftName != null) {
            this.giftName = giftName;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (receivedDate != null) {
            this.receivedDate = receivedDate;
        }
        if (reminderDate != null) {
            this.reminderDate = reminderDate;
        }
        if (thanked != null) {
            this.thanked = thanked;
        }
    }

    public void confirm() {
        this.status = GiftRecordStatus.CONFIRMED;
    }

    public void updateThanked(boolean thanked) {
        this.thanked = thanked;
    }
}
