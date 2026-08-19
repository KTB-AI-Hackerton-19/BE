package com.hackathon.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "헬스체크", description = "서버 생존 확인")
@RestController
public class HealthController {

    @Operation(summary = "서버 상태 확인", description = "서버가 살아있으면 OK 문자열을 반환한다.")
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
