package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선물 카테고리. 코드가 아니라 DB row로 관리한다.
 *
 * <p>카테고리를 하나 추가할 때 코드 수정/재컴파일/재배포가 필요 없도록 enum이 아닌 테이블로 뺐다.
 * 카테고리 추가 = row 1건 INSERT (또는 {@code POST /api/categories} 호출).
 * emoji/color도 여기 같이 두어 이모지·색상 매핑이 코드에 흩어지지 않게 했다.</p>
 *
 * <p><b>카테고리는 사용자별로 따로 갖는다.</b> 가입할 때 기본 7종이 그 사용자 것으로 복제되며,
 * 이후 추가·수정·숨김은 그 사용자에게만 영향을 준다. 이름 중복 검사도 사용자 안에서만 한다.</p>
 */
@Entity
@Table(name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_user_name", columnNames = {"user_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 이 카테고리가 어느 탭에 속하는지. 화면 상단의 "선물 / 경조사" 탭이 이 값으로 갈린다.
     * 경조사 안에서 경사(CELEBRATION)와 조사(CONDOLENCE)가 더 나뉜다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftKind kind;

    /** 화면에 그대로 노출되는 이름 (예: "디저트"). 같은 사용자 안에서 중복 불가 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 기록 카드 좌측에 표시되는 기본 이모지 (예: "🍰") */
    @Column(nullable = false, length = 16)
    private String emoji;

    /** 기록 카드 배경 테마 (mint / pink / blue / gold) — 프론트 CSS 클래스명과 1:1 */
    @Column(nullable = false, length = 20)
    private String color;

    /** 필터 칩 / select 옵션 정렬 순서 (작을수록 앞) */
    @Column(nullable = false)
    private Integer displayOrder;

    /** false면 목록 API에서 숨김. 이미 이 카테고리로 기록된 데이터는 그대로 유지됨 */
    @Column(nullable = false)
    private boolean active;

    /**
     * 행사일. 경조사 카테고리("내 결혼식", "아버지 장례식")에서 <b>행사가 실제로 열린 날</b>을 담는다.
     *
     * <p>기록의 receivedDate(축의금을 받은 날)와는 다르다 — 축의금은 행사 전후로 흩어져 들어오므로
     * 기록에서 역산하면 행사일이 하루씩 어긋난다. 그래서 사용자가 직접 입력받아 여기 저장한다.</p>
     *
     * <p>일반 선물(GIFT) 카테고리에는 의미가 없어 항상 null로 유지된다 — kind가 GIFT면 들어와도 버린다.</p>
     */
    @Column
    private LocalDate eventDate;

    public Category(User user, String name, String emoji, String color, Integer displayOrder, boolean active,
                    GiftKind kind) {
        this(user, name, emoji, color, displayOrder, active, kind, null);
    }

    public Category(User user, String name, String emoji, String color, Integer displayOrder, boolean active,
                    GiftKind kind, LocalDate eventDate) {
        this.user = user;
        this.kind = kind == null ? GiftKind.GIFT : kind;
        this.name = name;
        this.emoji = emoji;
        this.color = color;
        this.displayOrder = displayOrder;
        this.active = active;
        this.eventDate = this.kind.isEvent() ? eventDate : null;
    }

    public void update(String name, String emoji, String color, Integer displayOrder, Boolean active, GiftKind kind,
                       LocalDate eventDate) {
        if (kind != null) {
            this.kind = kind;
        }
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (emoji != null && !emoji.isBlank()) {
            this.emoji = emoji;
        }
        if (color != null && !color.isBlank()) {
            this.color = color;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        if (active != null) {
            this.active = active;
        }
        if (eventDate != null) {
            this.eventDate = eventDate;
        }
        // 선물 탭으로 옮겨진 카테고리에는 행사일이 남아 있으면 안 된다.
        if (!this.kind.isEvent()) {
            this.eventDate = null;
        }
    }
}
