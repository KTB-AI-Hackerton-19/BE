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

    public void markSent() {
        this.status = ReminderStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /** 화면에 토스트로 띄운 뒤 호출한다. */
    public void markDelivered() {
        this.delivered = true;
    }
}
