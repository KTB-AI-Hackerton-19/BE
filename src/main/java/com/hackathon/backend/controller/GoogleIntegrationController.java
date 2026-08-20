package com.hackathon.backend.controller;

import com.hackathon.backend.dto.ApiResponse;
import com.hackathon.backend.dto.integration.GoogleAuthorizeUrlResponse;
import com.hackathon.backend.dto.integration.GoogleCalendarStatusResponse;
import com.hackathon.backend.service.GoogleCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구글 캘린더 연동", description = "로그인한 회원에게 구글 캘린더 권한을 위임받고, 답례일자를 실제 일정으로 등록한다.")
@RestController
@RequestMapping("/api/integrations/google")
public class GoogleIntegrationController {

    private final GoogleCalendarService googleCalendarService;

    public GoogleIntegrationController(GoogleCalendarService googleCalendarService) {
        this.googleCalendarService = googleCalendarService;
    }

    @Operation(summary = "연동 상태 조회",
            description = "마이페이지에서 '연동됨/연동 안 됨'을 그리는 데 쓴다. reauthRequired=true면 다시 연동해야 한다.")
    @GetMapping
    public ApiResponse<GoogleCalendarStatusResponse> status() {
        return ApiResponse.success(googleCalendarService.status());
    }

    @Operation(summary = "동의 화면 URL 발급",
            description = "로그인 상태에서 호출한다. 응답의 authorizeUrl로 브라우저를 보내면 구글 동의 화면이 뜬다.")
    @GetMapping("/authorize-url")
    public ApiResponse<GoogleAuthorizeUrlResponse> authorizeUrl() {
        return ApiResponse.success(googleCalendarService.authorizeUrl());
    }

    /**
     * 구글이 브라우저를 되돌려보내는 지점. <b>JWT 없이 들어온다</b>(SecurityConfig에서 permitAll).
     * 회원 식별은 헤더가 아니라 서명된 state로 한다.
     */
    @Operation(summary = "구글 콜백(브라우저 전용)",
            description = "구글이 직접 호출한다. 처리 후 프론트로 302 리디렉트하며 ?google=connected|denied|failed 를 붙인다.")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String redirect = googleCalendarService.handleCallback(code, state, error);
        return ResponseEntity.status(302).location(URI.create(redirect)).build();
    }

    @Operation(summary = "연동 해제", description = "저장된 refresh token을 지운다. 이미 등록된 구글 일정은 그대로 남는다.")
    @DeleteMapping
    public ApiResponse<Void> disconnect() {
        googleCalendarService.disconnect();
        return ApiResponse.success(null);
    }
}
