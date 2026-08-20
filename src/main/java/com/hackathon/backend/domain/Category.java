package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * <p><b>카테고리는 사용자별로 따로 갖는다.</b> 가입할 때 기본 6종이 그 사용자 것으로 복제되며,
 * 이후 추가·수정·숨김은 그 사용자에게만 영향을 준다. 이름 중복 검사도 사용자 안에서만 한다.</p>
 *
 * <p><b>선물 전용이다.</b> 경조사는 {@link GiftRecord#getRecordType()}이 EVENT일 때
 * {@link EventCategory}(고정 7종)를 직접 쓰므로 여기 관여하지 않는다.</p>
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

    public Category(User user, String name, String emoji, String color, Integer displayOrder, boolean active) {
        this.user = user;
        this.name = name;
        this.emoji = emoji;
        this.color = color;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void update(String name, String emoji, String color, Integer displayOrder, Boolean active) {
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
    }
}
