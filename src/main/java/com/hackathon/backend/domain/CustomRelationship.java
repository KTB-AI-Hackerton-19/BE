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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 직접 추가한 관계. 기본 9종({@link Relationship})으로 부족할 때 만든다.
 *
 * <p><b>사용자별</b>이다 — 내가 만든 "동호회"가 다른 사람 드롭다운에 나타나면 안 된다.
 * {@link Category}와 같은 이유·같은 구조이며, 기본 9종은 모두에게 동일하므로 여기 복제하지 않는다
 * ({@code GET /api/relationships}가 둘을 합쳐서 내려준다).</p>
 *
 * <p>이름 자체가 곧 저장되는 값이라 (user, name)이 유니크다. 기본 9종과 겹치는 이름은
 * 서비스에서 미리 막는다 — 드롭다운에 같은 값이 두 줄로 나오기 때문이다.</p>
 */
@Entity
@Table(name = "custom_relationships",
        uniqueConstraints = @UniqueConstraint(name = "uk_custom_relationship_user_name",
                columnNames = {"user_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 드롭다운에 그대로 노출되고, 사람·기록에도 이 문자열이 그대로 저장된다 (예: "동호회") */
    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public CustomRelationship(User user, String name) {
        this.user = user;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }
}
