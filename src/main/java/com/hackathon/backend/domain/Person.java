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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "people")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    /**
     * 관계. 자유 입력이 아니라 {@code GET /api/relationships}의 값 중 하나를 고른 것이며,
     * 기본 9종의 한글 라벨("친구") 또는 사용자가 추가한 관계 이름("동호회")이 그대로 들어온다.
     * 선택 항목이라 미지정(null)을 허용한다.
     *
     * <p>enum이 아니라 문자열인 이유는 커스텀 관계가 enum이 될 수 없기 때문이다.
     * 아무 값이나 들어오지 않도록 저장 전에 {@code RelationshipService.normalize}를 반드시 통과시킨다.</p>
     */
    @Column(length = 50)
    private String relationship;

    /** 선택 항목. 입력하지 않으면 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column
    private LocalDate birthday;

    @Column
    private String memo;

    public Person(User user, String name, String relationship, Gender gender, LocalDate birthday, String memo) {
        this.user = user;
        this.name = name;
        this.relationship = relationship;
        this.gender = gender;
        this.birthday = birthday;
        this.memo = memo;
    }

    /** null로 들어온 필드는 기존 값을 유지한다(부분 수정 PATCH 시맨틱). */
    public void update(String name, String relationship, Gender gender, LocalDate birthday, String memo) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (relationship != null) {
            this.relationship = relationship;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthday != null) {
            this.birthday = birthday;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
