package com.hackathon.backend.dto;

import com.hackathon.backend.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;

    private ApiResponse(boolean success, T data, ErrorDetail error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code.name(), message));
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message));
    }

    /** 어느 입력칸이 문제인지까지 함께 내려보낸다. 프론트가 해당 인풋에 바로 표시할 수 있다. */
    public static <T> ApiResponse<T> error(ErrorCode code, String message, List<ErrorDetail.FieldError> fields) {
        return new ApiResponse<>(false, null, new ErrorDetail(code.name(), message, fields));
    }
}
