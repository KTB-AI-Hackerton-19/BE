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
 * 답례 알림. {@code GiftRecord.reminderDate}가 지정되면 1:1로 생성/동기화된다.
 *
 * <p>디자인의 모달이 "답례 알림일"을 절대 날짜로 직접 입력받기 때문에 anchorDate/offsetDays 계산 구조는 제거하고
 * scheduledAt(절대 날짜) 하나만 남겼다. PENDING/SENT 상태는 발송 스케줄러가 사용한다.</p>
 */
@Entity
@Table(name = "reminder_tasks", indexes = {
        @Index(name = "idx_reminder_status_scheduled", columnList = "status, scheduledAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_record_id")
    private GiftRecord giftRecord;

    /** 알림 예정일 (= GiftRecord.reminderDate) */
    @Column(nullable = false)
    private LocalDate scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status;

    /** 발송 처리된 시각. 화면에 아직 안 띄운 알림을 찾을 때 기준이 된다. */
    @Column
    private LocalDateTime sentAt;

    /** 사용자 화면에 토스트로 한 번 띄웠는지. true가 되면 다시 뜨지 않는다. */
    @Column(nullable = false)
    private boolean delivered;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * 구글 캘린더에 등록된 이벤트 id. 연동한 사용자에게만 채워진다.
     *
     * <p>답례일자가 바뀌면 새 일정을 또 만드는 게 아니라 이 id로 기존 일정을 옮겨야 한다.
     * 안 그러면 날짜를 두 번 고친 사용자의 캘린더에 유령 일정이 세 개 남는다.</p>
     */
    @Column(length = 200)
    private String googleEventId;

    /** 구글 캘린더에서 그 일정을 여는 링크. 등록 직후 화면에서 "캘린더에서 보기"로 쓴다. */
    @Column(length = 500)
    private String googleHtmlLink;

    public ReminderTask(User user, Person person, GiftRecord giftRecord, LocalDate scheduledAt) {
        this.user = user;
        this.person = person;
        this.giftRecord = giftRecord;
        this.scheduledAt = scheduledAt;
        this.status = ReminderStatus.PENDING;
        this.delivered = false;
        this.createdAt = LocalDateTime.now();
    }

    /** 기록의 reminderDate/person이 수정되면 알림도 같이 갱신하고 다시 PENDING으로 되돌린다. */
    public void reschedule(Person person, LocalDate scheduledAt) {
        this.person = person;
        this.scheduledAt = scheduledAt;
        this.status = ReminderStatus.PENDING;
        this.sentAt = null;
        this.delivered = false;
    }

    /**
     * 사람만 갈아끼운다. 리스트에만 있던 이름을 뒤늦게 Person에 연결할 때 쓴다.
     *
     * <p>{@link #reschedule}를 대신 쓰면 안 된다. 그쪽은 상태를 PENDING으로 되돌려서,
     * 이미 발송된 알림이 사람 연결 한 번에 다시 발송 대기로 살아난다.</p>
     */
    public void assignPerson(Person person) {
        this.person = person;
    }

    /** 구글 캘린더 등록/갱신 결과를 붙인다. */
    public void linkGoogleEvent(String googleEventId, String googleHtmlLink) {
        this.googleEventId = googleEventId;
        this.googleHtmlLink = googleHtmlLink;
    }

    public void clearGoogleEvent() {
        this.googleEventId = null;
        this.googleHtmlLink = null;
    }

    public void markSent() {
        this.status = ReminderStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /** 화면에 토스트로 띄운 뒤 호출한다. */
    public void markDelivered() {
        this.delivered = true;
    }
}
