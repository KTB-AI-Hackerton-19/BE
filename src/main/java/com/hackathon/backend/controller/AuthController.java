package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.auth.LoginRequest;
import com.hackathon.backend.dto.auth.RefreshRequest;
import com.hackathon.backend.dto.auth.SignupRequest;
import com.hackathon.backend.dto.auth.TokenResponse;
import com.hackathon.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입 / 로그인 / 로그아웃 / 토큰 재발급")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "아이디/비밀번호로 계정을 생성한다.")
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호를 검증하고 Access/Refresh 토큰을 발급한다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "로그아웃", description = "현재 로그인한 사용자의 refresh token을 무효화한다. Authorization 헤더에 access token 필요.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    @Operation(summary = "토큰 재발급", description = "유효한 refresh token으로 새 Access/Refresh 토큰 쌍을 발급한다.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }
}
