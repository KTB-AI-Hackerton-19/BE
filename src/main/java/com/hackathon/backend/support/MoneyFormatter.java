package com.hackathon.backend.support;

import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * 금액 표기 유틸.
 *
 * <p>DB에는 정수(원)로만 저장하고(필터·집계·정렬을 위해), 응답에는 프론트가 그대로 그릴 수 있는
 * "35,000원" 문자열을 함께 내려준다. 반대로 요청은 "35,000원" / "35000" / 35000 어떤 형태로 와도 받아준다.</p>
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
    }

    /** 35000 → "35,000원" (null이면 null) */
    public static String format(Integer amount) {
        if (amount == null) {
            return null;
        }
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    /**
     * "35,000원" / "35000" / " 35,000 원 " → 35000.
     *
     * <p>비어 있으면 null(금액 미입력). 하지만 <b>값을 넣었는데 숫자가 없거나 음수면 오류로 돌려준다.</b>
     * 예전에는 "-50000"에서 부호만 조용히 사라져 50,000원으로 저장됐고, "삼만원"은 금액이 사라진 채
     * 저장이 성공해서 사용자가 잘못을 알 수 없었다.</p>
     */
    public static Integer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        boolean negative = raw.trim().startsWith("-");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT,
                    "금액을 숫자로 입력해주세요. (예: 35000 또는 35,000원)");
        }
        if (negative) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "금액은 0원 이상이어야 합니다.");
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "금액이 너무 큽니다.");
        }
    }
}
