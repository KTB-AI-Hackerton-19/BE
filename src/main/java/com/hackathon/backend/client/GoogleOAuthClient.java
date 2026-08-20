package com.hackathon.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 구글 OAuth2 토큰 엔드포인트 호출. 구글 SDK를 쓰지 않는 이유는 우리가 필요한 게
 * "code → refresh token"과 "refresh token → access token" 두 번의 form POST뿐이기 때문이다.
 */
@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

    private final RestClient restClient;
    /** 기본값은 실제 구글 주소다. 테스트에서만 목 서버로 바꿔 끼운다. */
    private final String authEndpoint;
    private final String tokenEndpoint;
    private final String userinfoEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String scope;

    public GoogleOAuthClient(@Value("${google.oauth.client-id:}") String clientId,
                             @Value("${google.oauth.client-secret:}") String clientSecret,
                             @Value("${google.oauth.redirect-uri:}") String redirectUri,
                             @Value("${google.oauth.scope}") String scope,
                             @Value("${google.oauth.timeout-ms:10000}") int timeoutMs,
                             @Value("${google.oauth.auth-endpoint:https://accounts.google.com/o/oauth2/v2/auth}") String authEndpoint,
                             @Value("${google.oauth.token-endpoint:https://oauth2.googleapis.com/token}") String tokenEndpoint,
                             @Value("${google.oauth.userinfo-endpoint:https://www.googleapis.com/oauth2/v3/userinfo}") String userinfoEndpoint) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scope = scope;
        this.authEndpoint = authEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.userinfoEndpoint = userinfoEndpoint;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String scope() {
        return scope;
    }

    /**
     * 동의 화면 URL.
     *
     * <p>{@code access_type=offline}과 {@code prompt=consent}가 둘 다 있어야 refresh token이 온다.
     * 특히 prompt를 빼면 <b>두 번째 연동부터 refresh token이 오지 않아</b> 1시간 뒤에 조용히 죽는다.
     * 구글은 이 경우 에러 없이 access token만 주기 때문에 테스트에서 잡히지 않는다.</p>
     */
    public String authorizationUrl(String state) {
        requireConfigured();
        return authEndpoint
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(scope)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&include_granted_scopes=true"
                + "&state=" + encode(state);
    }

    /** 콜백으로 받은 code를 토큰으로 교환한다. */
    public TokenResponse exchangeCode(String code) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        return postToken(form, "구글 인증 코드 교환에 실패했습니다.");
    }

    /** 저장해둔 refresh token으로 access token을 새로 받는다. 응답에 refresh token은 없다. */
    public TokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        return postToken(form, "구글 토큰 갱신에 실패했습니다.");
    }

    /** 어느 계정에 연동됐는지 보여주기 위한 이메일 조회. 실패해도 연동 자체는 유효하므로 null을 돌려준다. */
    public String fetchEmail(String accessToken) {
        try {
            UserInfo info = restClient.get()
                    .uri(userinfoEndpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(UserInfo.class);
            return info == null ? null : info.email();
        } catch (RestClientException e) {
            log.warn("구글 사용자 이메일 조회 실패(연동은 계속 진행): {}", e.getMessage());
            return null;
        }
    }

    private TokenResponse postToken(MultiValueMap<String, String> form, String failureMessage) {
        try {
            TokenResponse response = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT, failureMessage);
            }
            return response;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.warn("구글 토큰 요청 실패 {} {}", e.getStatusCode(), body);
            // invalid_grant = 사용자가 권한을 철회했거나 code를 이미 썼다. 재연동 외에는 방법이 없다.
            if (body.contains("invalid_grant")) {
                throw new CustomException(ErrorCode.GOOGLE_REAUTH_REQUIRED,
                        "구글 연동이 만료되었습니다. 캘린더를 다시 연동해주세요.");
            }
            throw new CustomException(ErrorCode.INVALID_INPUT, failureMessage);
        } catch (RestClientException e) {
            log.warn("구글 토큰 요청 통신 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_INPUT, failureMessage);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "구글 캘린더 연동이 서버에 설정되지 않았습니다. (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / GOOGLE_REDIRECT_URI)");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Integer expiresIn,
            @JsonProperty("scope") String scope) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserInfo(String email) {
    }
}
