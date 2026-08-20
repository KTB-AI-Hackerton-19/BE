package com.hackathon.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 사람을 찾을 수 없습니다."),
    GIFT_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "선물 기록을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "이미 존재하는 카테고리 이름입니다."),
    DUPLICATE_RELATIONSHIP(HttpStatus.CONFLICT, "이미 존재하는 관계 이름입니다."),
    REMINDER_NOT_FOUND(HttpStatus.NOT_FOUND, "답례 알림을 찾을 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 형식입니다."),
    AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "AI 분석 서비스 호출에 실패했습니다."),
    GOOGLE_NOT_CONNECTED(HttpStatus.CONFLICT, "구글 캘린더가 연동되어 있지 않습니다."),
    GOOGLE_REAUTH_REQUIRED(HttpStatus.CONFLICT, "구글 연동이 만료되었습니다. 다시 연동해주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
