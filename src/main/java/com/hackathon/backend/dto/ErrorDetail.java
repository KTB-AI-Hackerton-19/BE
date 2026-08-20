package com.hackathon.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Getter;

/**
 * 실패 응답의 error 본문.
 *
 * <p>{@code message}는 토스트에 그대로 띄우는 한 문장이고, {@code fields}는 <b>어느 입력칸이 문제인지</b>를
 * 항목별로 담는다. 프론트가 메시지 문자열을 파싱하지 않고도 해당 인풋에 빨간 테두리와 문구를 붙일 수 있게
 * 하려는 것이다 — {@code field} 값은 <b>요청 필드명과 동일</b>하고, 이는 화면 폼의 상태 이름과도 같다
 * (예: 기록 모달의 personName/gift/price, 사람 등록의 name/gender/relation).</p>
 *
 * <p>필드를 특정할 수 없는 에러(404, 서버 오류 등)에서는 {@code fields}가 아예 내려가지 않는다.</p>
 */
@Getter
public class ErrorDetail {

    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<FieldError> fields;

    public ErrorDetail(String code, String message) {
        this(code, message, List.of());
    }

    public ErrorDetail(String code, String message, List<FieldError> fields) {
        this.code = code;
        this.message = message;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /**
     * 문제가 있는 입력칸 하나.
     *
     * @param field   요청/폼 필드명 (personName, gift, price, name, gender, relation ...)
     * @param message 그 칸 아래에 그대로 띄울 문구
     */
    public record FieldError(String field, String message) {
    }
}
