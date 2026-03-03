package com.qaima.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompanyNameNormalizer {

    private static final Pattern KOR_CORP_PATTERN = Pattern.compile(
            "(?i)\\(\\s*\\uC8FC\\s*\\)|" +
            "\\(\\s*\\uC720\\s*\\)|" +
            "\\(\\s*\\uC8FC\\uC2DD\\uD68C\\uC0AC\\s*\\)|" +
            "\\(\\s*\\uC720\\uD55C\\uD68C\\uC0AC\\s*\\)|" +
            "\\u3231|" +
            "\\uC8FC\\uC2DD\\uD68C\\uC0AC|" +
            "\\uC720\\uD55C\\uD68C\\uC0AC"
    );
    private static final Pattern KOR_PREFERRED_PATTERN = Pattern.compile(
            "\\uC6B0\\uC120\\uC8FC|\\uC6B0\\uC120|\\uC885\\uB958\\uC8FC|\\uC885\\uB958"
    );
    private static final Pattern KOR_COMMON_PATTERN = Pattern.compile("\\uBCF4\\uD1B5\\uC8FC");
    private static final Pattern PREFERRED_NUM_SUFFIX_PATTERN = Pattern.compile("\\uC6B0\\s*(\\d+)");
    private static final Pattern ENG_CORP_PATTERN = Pattern.compile(
            "(?i)\\b(inc|inc\\.|corp|corp\\.|co|co\\.|company|ltd|ltd\\.|limited|plc|llc)\\b"
    );
    private static final Pattern PAREN_PATTERN = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern ENGLISH_PAREN_CONTENT = Pattern.compile("^[A-Za-z0-9 .,&/+-]*$");
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[\\[\\]\\{\\}<>]");
    private static final Pattern PUNCT_OR_SPACE = Pattern.compile("[\\s\\p{Punct}\\u00B7\\u2022\\u318D]");

    private CompanyNameNormalizer() {
    }

    public static String normalizeKey(String input) {
        String cleaned = clean(input);
        if (cleaned.isEmpty()) {
            return "";
        }

        cleaned = stripCorporateSuffixes(cleaned);
        cleaned = normalizePreferredShare(cleaned);
        cleaned = stripParentheticalEnglish(cleaned);
        cleaned = BRACKET_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = ENG_CORP_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = PUNCT_OR_SPACE.matcher(cleaned).replaceAll("");
        return cleaned.toUpperCase(Locale.ROOT);
    }

    public static String extractSearchKeyword(String input) {
        String cleaned = clean(input);
        if (cleaned.isEmpty()) {
            return "";
        }

        cleaned = stripCorporateSuffixes(cleaned);
        cleaned = normalizePreferredShare(cleaned);
        cleaned = stripParentheticalEnglish(cleaned);
        cleaned = BRACKET_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = ENG_CORP_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = PUNCT_OR_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.trim();
        if (cleaned.isEmpty()) {
            return "";
        }

        String longest = "";
        for (String token : cleaned.split("\\s+")) {
            if (token.length() > longest.length()) {
                longest = token;
            }
        }
        return longest.isEmpty() ? cleaned : longest;
    }

    public static String normalizeSearchQuery(String input) {
        String cleaned = clean(input);
        if (cleaned.isEmpty()) {
            return "";
        }
        return normalizePreferredShare(cleaned).trim();
    }

    private static String clean(String input) {
        if (input == null) {
            return "";
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        return trimmed
                .replace('\uFF08', '(')
                .replace('\uFF09', ')');
    }

    private static String stripCorporateSuffixes(String input) {
        return KOR_CORP_PATTERN.matcher(input).replaceAll(" ");
    }

    private static String stripParentheticalEnglish(String input) {
        Matcher matcher = PAREN_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1);
            if (inner != null && ENGLISH_PAREN_CONTENT.matcher(inner).matches()) {
                matcher.appendReplacement(sb, " ");
            } else {
                String replacement = inner == null ? "" : " " + inner + " ";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String normalizePreferredShare(String input) {
        String normalized = KOR_PREFERRED_PATTERN.matcher(input).replaceAll("\uC6B0");
        normalized = KOR_COMMON_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = PREFERRED_NUM_SUFFIX_PATTERN.matcher(normalized).replaceAll("$1\uC6B0");
        return normalized;
    }
}
