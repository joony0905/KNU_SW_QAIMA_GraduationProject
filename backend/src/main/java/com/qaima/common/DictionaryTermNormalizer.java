package com.qaima.common;

import java.util.Locale;

/**
 * 용어를 저장/조회할 때 일관되게 사용하도록 정규화합니다.
 *
 * 규칙:
 * - 앞뒤 공백 제거 + 연속 공백 축약
 * - 대문자 변환(금융 용어는 대소문자 비구분이 일반적)
 * - ㄱ~ㅎ / A~Z 인덱스 UI용 initial 계산
 */
public final class DictionaryTermNormalizer {

    private static final String[] CHOSEONG = {
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ",
            "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    private DictionaryTermNormalizer() {}

    public static String normalizeTerm(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isBlank()) return null;
        String collapsed = trimmed.replaceAll("\\s+", " ");
        return collapsed.toUpperCase(Locale.ROOT);
    }

    public static String computeInitial(String normalizedTerm) {
        if (normalizedTerm == null) return "#";
        String t = normalizedTerm.trim();
        if (t.isEmpty()) return "#";

        char ch = t.charAt(0);

        // 한글 완성형 음절(가~힣)
        if (ch >= 0xAC00 && ch <= 0xD7A3) {
            int base = ch - 0xAC00;
            int index = base / (21 * 28);
            if (index >= 0 && index < CHOSEONG.length) {
                return CHOSEONG[index];
            }
            return "#";
        }

        // 한글 자음 자모(ㄱ~ㅎ 등)
        if (ch >= 0x3131 && ch <= 0x314E) {
            return String.valueOf(ch);
        }

        // 영문 대문자 A-Z
        if (ch >= 'A' && ch <= 'Z') {
            return String.valueOf(ch);
        }
        if (ch >= 'a' && ch <= 'z') {
            return String.valueOf(Character.toUpperCase(ch));
        }

        return "#";
    }
}
