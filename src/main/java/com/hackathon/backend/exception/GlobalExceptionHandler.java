package com.hackathon.backend.exception;

import com.hackathon.backend.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리.
 *
 * <p><b>원칙: 사용자가 잘못 보낸 것은 4xx, 서버가 잘못한 것만 5xx.</b>
 * 잘못된 날짜 형식이나 너무 긴 문자열까지 500으로 나가면 프론트는 "서버가 터졌다"로 오해하고,
 * 사용자에게는 무엇을 고쳐야 하는지 안내할 수 없다.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, e.getMessage()));
    }

    /** @Valid 실패 — 첫 번째 필드 메시지를 그대로 보여준다(사용자가 읽을 문장으로 써두었다). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT.getDefaultMessage());
        return badRequest(message);
    }

    /**
     * 쿼리 파라미터 타입 불일치 — {@code ?startDate=abcd}, {@code ?status=NOPE} 등.
     * 어떤 파라미터가 문제인지 이름까지 알려줘야 프론트가 바로 고칠 수 있다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("'%s' 값이 올바르지 않습니다: %s".formatted(e.getName(), e.getValue()));
    }

    /** 필수 쿼리 파라미터 누락 — 예: 다중 삭제에서 ids를 아예 안 보낸 경우. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return badRequest("'%s' 값을 보내주세요.".formatted(e.getParameterName()));
    }

    /** 바디가 깨진 JSON이거나 형식이 안 맞을 때. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return badRequest("요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.");
    }

    /** Content-Type 누락/불일치. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type: application/json 으로 보내주세요."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("METHOD_NOT_ALLOWED",
                        "%s 메서드는 이 경로에서 지원하지 않습니다.".formatted(e.getMethod())));
    }

    /**
     * DB 제약 위반 — 컬럼 길이 초과, NOT NULL 위반, 유니크 중복 등.
     * 대부분 사용자 입력이 원인이라 400으로 내보내되, 원인 파악을 위해 로그는 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return badRequest("입력한 값이 저장 가능한 범위를 벗어났습니다. 길이나 형식을 확인해주세요.");
    }

    /** 여기까지 온 것은 진짜 서버 문제다. 스택트레이스를 남겨야 원인을 찾을 수 있다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, e.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, message));
    }
}
