package com.qaima.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class NewsArticleExtractorClient {

    private static final int LOW_CONFIDENCE_LENGTH_THRESHOLD = 80;
    private static final int MIN_PARAGRAPH_LENGTH = 35;
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("(?is)<article\\b[^>]*>(.*?)</article>");
    private static final Pattern MAIN_PATTERN = Pattern.compile("(?is)<main\\b[^>]*>(.*?)</main>");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p>");
    private static final List<String> BOILERPLATE_KEYWORDS = List.of(
            "copyright", "무단전재", "재배포", "구독", "광고", "기사제보", "제보는", "공유하기",
            "좋아요", "댓글", "기자", "입력 ", "수정 ", "메일", "뉴스레터", "관련기사", "맨위로"
    );

    private final WebClient webClient;

    public NewsArticleExtractorClient(@Qualifier("defaultWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public ArticleExtractionResult fetchArticleBody(String url) {
        String html = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (html == null || html.isBlank()) {
            return new ArticleExtractionResult(null, false);
        }

        ExtractionCandidate candidate = extractCandidate(html);
        if (candidate.body() == null || candidate.body().isBlank()) {
            return new ArticleExtractionResult(null, false);
        }

        return new ArticleExtractionResult(candidate.body(), candidate.lowConfidence());
    }

    private ExtractionCandidate extractCandidate(String html) {
        String sanitized = removeNoiseTags(html);

        List<String> articleParagraphs = extractParagraphs(findFirstGroup(sanitized, ARTICLE_PATTERN));
        if (!articleParagraphs.isEmpty()) {
            return buildCandidate(articleParagraphs, false);
        }

        List<String> mainParagraphs = extractParagraphs(findFirstGroup(sanitized, MAIN_PATTERN));
        if (!mainParagraphs.isEmpty()) {
            return buildCandidate(mainParagraphs, false);
        }

        List<String> paragraphTags = extractParagraphs(sanitized);
        if (!paragraphTags.isEmpty()) {
            return buildCandidate(paragraphTags, false);
        }

        String fallback = cleanText(sanitized);
        if (fallback == null || fallback.isBlank()) {
            return new ExtractionCandidate(null, true);
        }

        return new ExtractionCandidate(fallback, true);
    }

    private ExtractionCandidate buildCandidate(List<String> paragraphs, boolean forceLowConfidence) {
        String body = String.join("\n\n", paragraphs).trim();
        int paragraphCount = paragraphs.size();
        boolean lowConfidence = forceLowConfidence
                || body.length() < LOW_CONFIDENCE_LENGTH_THRESHOLD
                || paragraphCount < 2;
        return new ExtractionCandidate(body, lowConfidence);
    }

    private String removeNoiseTags(String html) {
        return html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?>.*?</noscript>", " ")
                .replaceAll("(?is)<nav.*?>.*?</nav>", " ")
                .replaceAll("(?is)<header.*?>.*?</header>", " ")
                .replaceAll("(?is)<footer.*?>.*?</footer>", " ");
    }

    private String findFirstGroup(String html, Pattern pattern) {
        if (html == null || html.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<String> extractParagraphs(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        Matcher matcher = PARAGRAPH_PATTERN.matcher(html);
        while (matcher.find()) {
            String cleaned = cleanText(matcher.group(1));
            if (isMeaningfulParagraph(cleaned)) {
                paragraphs.add(cleaned);
            }
        }
        return paragraphs;
    }

    private boolean isMeaningfulParagraph(String paragraph) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        if (paragraph.length() < MIN_PARAGRAPH_LENGTH) {
            return false;
        }
        String normalized = paragraph.toLowerCase(Locale.ROOT);
        for (String boilerplate : BOILERPLATE_KEYWORDS) {
            if (normalized.contains(boilerplate.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public record ArticleExtractionResult(
            String body,
            boolean lowConfidence
    ) {
    }

    private record ExtractionCandidate(
            String body,
            boolean lowConfidence
    ) {
    }
}
