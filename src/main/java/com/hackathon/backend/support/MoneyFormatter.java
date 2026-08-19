package com.hackathon.backend.support;

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

    /** "35,000원" / "35000" / " 35,000 원 " → 35000. 숫자가 하나도 없으면 null */
    public static Integer parse(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
