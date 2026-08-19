package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    /** 화면에 표시할 이름. 로그인에는 쓰지 않는다(로그인은 username). */
    @Column(nullable = false)
    private String name;

    /** 프로필 이미지의 S3 key. 가입 시에는 받지 않고, 프로필 수정에서만 설정한다. 없으면 null. */
    @Column
    private String profileImageKey;

    @Column
    private String refreshToken;

    public User(String username, String password, String name) {
        this.username = username;
        this.password = password;
        this.name = name;
    }

    /** 프로필 수정. null로 들어온 필드는 기존 값을 유지한다(부분 수정 PATCH 시맨틱). */
    public void updateProfile(String name, String profileImageKey) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (profileImageKey != null && !profileImageKey.isBlank()) {
            this.profileImageKey = profileImageKey.trim();
        }
    }

    /** 프로필 이미지 제거(기본 아바타로 되돌리기). */
    public void clearProfileImage() {
        this.profileImageKey = null;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
