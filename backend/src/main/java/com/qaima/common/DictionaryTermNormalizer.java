package com.qaima.common;

import java.util.Locale;

public final class DictionaryTermNormalizer {

    private static final String[] CHOSEONG = {
            "\u3131", "\u3132", "\u3134", "\u3137", "\u3138",
            "\u3139", "\u3141", "\u3142", "\u3143", "\u3145",
            "\u3146", "\u3147", "\u3148", "\u3149", "\u314A",
            "\u314B", "\u314C", "\u314D", "\u314E"
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
        String term = normalizedTerm.trim();
        if (term.isEmpty()) return "#";

        char ch = term.charAt(0);
        if (ch >= 0xAC00 && ch <= 0xD7A3) {
            int base = ch - 0xAC00;
            int index = base / (21 * 28);
            if (index >= 0 && index < CHOSEONG.length) {
                return CHOSEONG[index];
            }
            return "#";
        }

        if (ch >= 0x3131 && ch <= 0x314E) {
            return String.valueOf(ch);
        }

        if (ch >= 'A' && ch <= 'Z') {
            return String.valueOf(ch);
        }

        if (ch >= 'a' && ch <= 'z') {
            return String.valueOf(Character.toUpperCase(ch));
        }

        return "#";
    }
}
