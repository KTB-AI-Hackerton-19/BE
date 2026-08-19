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

    @Column
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
        if (relationship != null && !relationship.isBlank()) {
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
