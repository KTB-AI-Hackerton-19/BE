package com.hackathon.backend.exception;

import com.hackathon.backend.dto.ErrorDetail;
import java.util.List;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 문제가 있는 입력칸 목록. 비어 있으면 응답에 실리지 않는다.
     * 값이 있으면 프론트가 메시지를 파싱하지 않고도 해당 인풋에 바로 표시할 수 있다.
     */
    private final List<ErrorDetail.FieldError> fields;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), List.of());
    }

    public CustomException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public CustomException(ErrorCode errorCode, String message, List<ErrorDetail.FieldError> fields) {
        super(message);
        this.errorCode = errorCode;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /** 입력칸 하나가 비었거나 잘못됐을 때. 메시지는 그 칸에 그대로 띄울 문구를 쓴다. */
    public static CustomException field(String field, String message) {
        return new CustomException(ErrorCode.INVALID_INPUT, message,
                List.of(new ErrorDetail.FieldError(field, message)));
    }
}
