package com.hackathon.backend.service;

import com.hackathon.backend.domain.User;
import com.hackathon.backend.dto.auth.LoginRequest;
import com.hackathon.backend.dto.auth.RefreshRequest;
import com.hackathon.backend.dto.auth.SignupRequest;
import com.hackathon.backend.dto.auth.TokenResponse;
import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import com.hackathon.backend.repository.UserRepository;
import com.hackathon.backend.security.JwtProvider;
import com.hackathon.backend.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CategoryService categoryService;
    private final RecommendationPrefetcher recommendationPrefetcher;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       CategoryService categoryService, RecommendationPrefetcher recommendationPrefetcher) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.categoryService = categoryService;
        this.recommendationPrefetcher = recommendationPrefetcher;
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()), request.name().trim());
        userRepository.save(user);
        // 카테고리는 사용자별이라, 가입하는 순간 기본 7종을 그 사용자 것으로 깔아준다.
        categoryService.provisionDefaults(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        // 로그인 직후 홈 추천을 미리 만들어 둔다. 로그인 → 홈 사이의 몇백 ms가 AI를 기다릴 시간을 벌어준다.
        // 특히 서버를 재시작하면 인메모리 H2라 추천 캐시가 통째로 비는데, 그 첫 진입이 여기서 덮인다.
        recommendationPrefetcher.warmUpcoming(user.getUsername(), RecommendationService.DEFAULT_LIMIT);

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();

        if (!jwtProvider.validateToken(token) || !jwtProvider.isRefreshToken(token)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String username = jwtProvider.getUsername(token);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!token.equals(user.getRefreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        return issueTokens(user);
    }

    @Transactional
    public void logout() {
        String username = SecurityUtils.getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.updateRefreshToken(null);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getUsername());
        String refreshToken = jwtProvider.createRefreshToken(user.getUsername());
        user.updateRefreshToken(refreshToken);
        return new TokenResponse(accessToken, refreshToken, user.getName());
    }
}
