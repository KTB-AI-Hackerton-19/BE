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
 * 나머지는 그대로 컬럼.</p>
 *
 * <p><b>{@link #recordType}이 GIFT/EVENT를 가른다.</b> GIFT면 {@link #category}(사용자별 자유 카테고리)와
 * {@link #occasion}(자유 텍스트)을 쓰고, EVENT면 {@link #eventCategory}(고정 7종)와 {@link #eventDate}
 * (행사가 실제 열린 날)를 쓴다. 두 짝이 섞인 상태("선물인데 eventCategory 있음")가 저장되지 않도록
 * {@link #applyRecordType}이 한쪽을 항상 비운다 — 앞뒤가 안 맞는 조합이 아예 만들어지지 않게 하려는 것이다.</p>
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

    /** 대분류. GIFT면 category/occasion을, EVENT면 eventCategory/eventDate를 쓴다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordType recordType;

    /** 선물 카테고리. recordType=GIFT일 때만 값이 있다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** 경조사 유형(고정 7종). recordType=EVENT일 때만 값이 있다 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EventCategory eventCategory;

    /**
     * 행사가 실제로 열린 날. recordType=EVENT일 때만 값이 있다.
     *
     * <p>기록의 receivedDate(축의금을 받은 날)와는 다르다 — 축의금은 행사 전후로 흩어져 들어오므로
     * 기록에서 역산하면 행사일이 하루씩 어긋난다. 그래서 사용자가 직접 입력받아 여기 저장한다.</p>
     */
    @Column
    private LocalDate eventDate;

    /** S3 원본 이미지 key (응답에는 presigned GET URL로 변환해서 내려감) */
    @Column
    private String imageKey;

    /** AI가 이미지에서 읽어낸 보낸 사람 이름 (확정 전 참고용) */
    @Column
    private String extractedSenderName;

    /** AI가 추측한 관계 (확정 전 참고용). AI는 자유 텍스트를 주지만 저장할 땐 드롭다운에 있는 값으로 맞춰 넣는다 */
    @Column(length = 50)
    private String extractedRelationship;

    /**
     * AI가 추정한 보낸 사람의 나이·성별 (확정 전 참고용).
     *
     * <p>사람(Person)이 아니라 기록에 둔 이유는, DRAFT 시점에는 아직 사람이 정해지지 않았기 때문이다
     * (이름이 정확히 일치할 때만 연결된다). 사용자가 확인 폼에서 "새 사람 등록"을 고를 때
     * 이 값을 프리필로 쓰라고 응답에 함께 내려보낸다. AI 추정치를 기존 사람 정보에 자동으로 덮어쓰지는 않는다.</p>
     */
    @Column
    private Integer extractedAge;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender extractedGender;

    /**
     * 사람(Person)으로 등록하지 않은 보낸 사람 이름.
     *
     * <p>경조사는 한 번에 수십 명이 들어오는데 그 전원을 "사람들" 목록에 올릴 이유가 없다.
     * 그래서 <b>이름만 기록에 들고</b> 경조사 리스트에서만 보이게 하고, 나중에 사용자가 직접 고른 사람만
     * {@code POST /api/gift-records/{id}/person}으로 Person에 연결한다.</p>
     *
     * <p>{@link #extractedSenderName}과 나눈 이유: 그쪽은 <b>AI가 읽어낸 원본</b>이라 손대지 않는다
     * (오탈자 대조와 나중의 재매칭에 쓴다). 사용자가 고쳐 적은 이름은 여기로 들어온다.</p>
     */
    @Column(length = 100)
    private String guestName;

    /** 사람 미등록 상태에서 사용자가 지정한 관계. Person 없이도 답례 문구·추천 근거가 비지 않게 한다. */
    @Column(length = 50)
    private String guestRelationship;

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

    public static GiftRecord createConfirmed(User user, Person person, String guestName, String guestRelationship,
                                             RecordType recordType, Category category, EventCategory eventCategory,
                                             LocalDate eventDate, String occasion,
                                             String giftName, Integer amount, LocalDate receivedDate,
                                             LocalDate reminderDate, boolean thanked) {
        GiftRecord record = new GiftRecord();
        record.user = user;
        record.person = person;
        record.guestName = guestName;
        record.guestRelationship = guestRelationship;
        record.applyRecordType(recordType, category, eventCategory, eventDate);
        record.occasion = record.recordType == RecordType.GIFT ? occasion : null;
        record.giftName = giftName;
        record.amount = amount;
        record.receivedDate = receivedDate;
        record.reminderDate = reminderDate;
        record.thanked = thanked;
        record.status = GiftRecordStatus.CONFIRMED;
        record.createdAt = LocalDateTime.now();
        return record;
    }

    public static GiftRecord createDraft(User user, Person person, String imageKey, String extractedSenderName,
                                         String extractedRelationship, Integer extractedAge,
                                         Gender extractedGender, RecordType recordType, Category category,
                                         EventCategory eventCategory, LocalDate eventDate, String occasion,
                                         String giftName, Integer amount, LocalDate receivedDate,
                                         LocalDate reminderDate) {
        GiftRecord record = new GiftRecord();
        record.user = user;
        record.person = person;
        record.imageKey = imageKey;
        record.extractedSenderName = extractedSenderName;
        record.extractedRelationship = extractedRelationship;
        record.extractedAge = extractedAge;
        record.extractedGender = extractedGender;
        // 사람으로 등록하지 않아도 리스트에서 바로 이름이 보이고 수정도 되도록, AI 이름을 표시 이름의 초기값으로 둔다.
        record.guestName = extractedSenderName;
        record.applyRecordType(recordType, category, eventCategory, eventDate);
        record.occasion = record.recordType == RecordType.GIFT ? occasion : null;
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
    public void applyUpdate(Person person, String guestName, String guestRelationship, RecordType recordType,
                            Category category, EventCategory eventCategory, LocalDate eventDate, String occasion,
                            String giftName, Integer amount, LocalDate receivedDate, LocalDate reminderDate,
                            Boolean thanked) {
        if (person != null) {
            this.person = person;
        }
        if (guestName != null) {
            this.guestName = guestName;
        }
        if (guestRelationship != null) {
            this.guestRelationship = guestRelationship;
        }
        if (recordType != null || category != null || eventCategory != null || eventDate != null) {
            applyRecordType(
                    recordType != null ? recordType : this.recordType,
                    category != null ? category : this.category,
                    eventCategory != null ? eventCategory : this.eventCategory,
                    eventDate != null ? eventDate : this.eventDate);
        }
        if (occasion != null) {
            this.occasion = this.recordType == RecordType.GIFT ? occasion : null;
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

    /**
     * 화면·캘린더·알림에 쓸 보낸 사람 이름. 등록된 사람 → 사용자가 적은 이름 → AI 원본 순으로 고른다.
     *
     * <p>이 폴백을 한 군데로 모아둔 이유는, 예전에 호출부마다 {@code person.getName()}만 읽어서
     * 사람 미등록 기록이 캘린더·알림 목록에서 이름 없이 나가는 누락이 반복됐기 때문이다.</p>
     */
    public String displayName() {
        if (person != null) {
            return person.getName();
        }
        if (guestName != null && !guestName.isBlank()) {
            return guestName;
        }
        return extractedSenderName;
    }

    /** 위와 같은 순서로 고른 관계. */
    public String displayRelationship() {
        if (person != null && person.getRelationship() != null) {
            return person.getRelationship();
        }
        return guestRelationship != null ? guestRelationship : extractedRelationship;
    }

    /**
     * 리스트에만 있던 이름을 사람(Person)에 연결한다.
     *
     * <p>{@link #guestName}은 지우지 않는다. 사용자가 사람을 지웠을 때 기록이 이름 없는 껍데기로 남지 않게
     * 되돌아갈 자리가 필요하기 때문이다.</p>
     */
    public void linkPerson(Person person) {
        this.person = person;
    }

    public void confirm() {
        this.status = GiftRecordStatus.CONFIRMED;
    }

    /**
     * recordType에 맞춰 category/eventCategory/eventDate를 일관되게 맞춘다.
     * GIFT면 eventCategory/eventDate를, EVENT면 category를 강제로 비워 앞뒤 안 맞는 조합을 막는다.
     */
    private void applyRecordType(RecordType recordType, Category category, EventCategory eventCategory,
                                 LocalDate eventDate) {
        this.recordType = recordType == null ? RecordType.GIFT : recordType;
        if (this.recordType == RecordType.EVENT) {
            this.category = null;
            this.eventCategory = eventCategory;
            this.eventDate = eventDate;
        } else {
            this.category = category;
            this.eventCategory = null;
            this.eventDate = null;
        }
    }

    /** 카테고리가 삭제될 때 남은 기록을 다른 카테고리("기타")로 옮긴다. GIFT 기록에서만 호출된다. */
    public void changeCategory(Category category) {
        this.category = category;
    }

    public void updateThanked(boolean thanked) {
        this.thanked = thanked;
    }
}
