package com.hackathon.backend.dto.integration;

/** 프론트가 이 URL로 이동시키면 구글 동의 화면이 뜬다. */
public record GoogleAuthorizeUrlResponse(String authorizeUrl) {
}
