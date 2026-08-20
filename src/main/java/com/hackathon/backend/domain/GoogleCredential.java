package com.hackathon.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 한 명의 구글 캘린더 연동 정보. <b>이 행의 존재 여부가 곧 "연동됨" 상태다.</b>
 *
 * <p>로그인(username/password + JWT)과는 완전히 별개다. 구글로 로그인하는 게 아니라
 * 이미 로그인한 회원이 캘린더 권한만 추가로 위임하는 것이라, User 쪽은 아무것도 바뀌지 않는다.</p>
 *
 * <p>refresh token만 보관한다. access token은 1시간이면 죽어서 저장할 가치가 없고,
 * 필요할 때 refresh token으로 새로 받는다.</p>
 */
@Entity
@Table(name = "google_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * 구글이 최초 동의 때 한 번만 주는 값. 이게 있으면 사용자가 다시 로그인하지 않아도
     * 계속 access token을 받아올 수 있다.
     */
    @Column(nullable = false, length = 512)
    private String refreshToken;

    /** 어느 구글 계정에 연동됐는지 화면에 보여주기 위한 값. 회원 식별에는 쓰지 않는다. */
    @Column
    private String googleEmail;

    @Column(nullable = false, length = 512)
    private String scope;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    /**
     * refresh token이 폐기됐을 때(사용자가 구글 계정 설정에서 앱 권한을 철회) 남기는 사유.
     * 값이 있으면 재연동이 필요하다는 뜻이라 화면에서 "다시 연동해주세요"를 띄운다.
     */
    @Column(length = 500)
    private String revokedReason;

    public GoogleCredential(User user, String refreshToken, String googleEmail, String scope) {
        this.user = user;
        this.refreshToken = refreshToken;
        this.googleEmail = googleEmail;
        this.scope = scope;
        this.connectedAt = LocalDateTime.now();
    }

    /** 같은 회원이 다시 연동하면 새 토큰으로 갈아끼운다(계정을 바꿔 연동하는 경우 포함). */
    public void reconnect(String refreshToken, String googleEmail, String scope) {
        this.refreshToken = refreshToken;
        this.googleEmail = googleEmail;
        this.scope = scope;
        this.connectedAt = LocalDateTime.now();
        this.revokedReason = null;
    }

    public void markRevoked(String reason) {
        this.revokedReason = reason;
    }

    public boolean isRevoked() {
        return revokedReason != null;
    }
}
