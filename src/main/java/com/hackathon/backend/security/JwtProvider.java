package com.hackathon.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_STATE = "oauth_state";

    /**
     * 구글 동의 화면에 실어 보내는 state의 수명. 사용자가 동의 버튼을 누르기까지의 시간이라
     * 넉넉히 10분이면 충분하고, 길게 잡을수록 가로챈 state를 재사용할 여지만 커진다.
     */
    private static final long STATE_EXPIRATION_MS = 10 * 60 * 1000L;

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        // 비어 있으면 여기서 크게 터뜨린다. 기본값을 심어 두면 모두가 아는 키로 토큰을 서명한 채
        // 아무 경고 없이 뜨는데, 그건 인증이 없는 것과 같다.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret이 비어 있습니다. "
                    + "config/secrets.yml.example을 config/secrets.yml로 복사해 값을 채우거나, "
                    + "JWT_SECRET 환경변수를 설정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String createAccessToken(String username) {
        return createToken(username, TYPE_ACCESS, accessTokenExpirationMs);
    }

    public String createRefreshToken(String username) {
        return createToken(username, TYPE_REFRESH, refreshTokenExpirationMs);
    }

    /**
     * 구글 OAuth의 {@code state}로 쓸 단기 토큰.
     *
     * <p>구글 콜백은 우리 JWT 헤더 없이 브라우저 리디렉트로 들어오기 때문에, 그 요청만 보고는
     * 어느 회원인지 알 수 없다. 그래서 연동을 시작한 회원을 서명해 state에 실어 보내고
     * 콜백에서 다시 검증한다. 서명이 있으므로 남이 state를 지어내 남의 계정에 붙일 수 없다.</p>
     */
    public String createOAuthStateToken(String username) {
        return createToken(username, TYPE_STATE, STATE_EXPIRATION_MS);
    }

    /** state 토큰을 검증하고 회원 아이디를 꺼낸다. 위조·만료·타입 불일치면 null. */
    public String usernameFromOAuthState(String state) {
        if (state == null || state.isBlank() || !validateToken(state)) {
            return null;
        }
        Claims claims = getClaims(state);
        return TYPE_STATE.equals(claims.get(CLAIM_TYPE, String.class)) ? claims.getSubject() : null;
    }

    private String createToken(String username, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(getClaims(token).get(CLAIM_TYPE, String.class));
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
