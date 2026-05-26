package com.qaima.external;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class NewsArticleExtractorClient {

    private static final int LOW_CONFIDENCE_LENGTH_THRESHOLD = 120;
    private static final int MIN_PARAGRAPH_LENGTH = 18;
    private static final int LONG_PARAGRAPH_LENGTH = 80;
    private static final double HIGH_LINK_DENSITY = 0.35d;
    private static final double CLOSE_SCORE_GAP = 6.0d;
    private static final String GLOBAL_NOISE_SELECTOR =
            "script,style,noscript,iframe,svg,form,nav,header,footer,aside";
    private static final String[] CANDIDATE_SELECTORS = {
            "article",
            "main",
            "[id*=article]",
            "[id*=news]",
            "[id*=content]",
            "[id*=view]",
            "[class*=article]",
            "[class*=news]",
            "[class*=content]",
            "[class*=view]",
            "[class*=body]"
    };
    private static final List<String> REMOVABLE_NODE_HINTS = List.of(
            "ad", "ads", "banner", "promotion", "recommend", "related", "popular",
            "comment", "reply", "share", "journalist", "reporter", "author",
            "copyright", "subscribe", "newsletter"
    );
    private static final List<String> BODY_LIKE_HINTS = List.of(
            "article", "articlebody", "article_txt", "news", "content", "view", "body", "story", "main"
    );
    private static final List<String> BOILERPLATE_KEYWORDS = List.of(
            "copyright", "무단전재", "재배포", "구독", "광고", "기사제보", "제보는", "공유하기",
            "좋아요", "댓글", "뉴스레터", "관련기사", "맨위로", "기자 구독", "기사 원문", "추천 기사"
    );
    private static final List<String> NOISE_PARAGRAPH_KEYWORDS = List.of(
            "공유", "좋아요", "댓글", "기사제보", "뉴스레터", "무단전재", "재배포", "광고", "구독"
    );

    private final WebClient webClient;

    public NewsArticleExtractorClient(@Qualifier("newsArticleWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public ArticleExtractionResult fetchArticleBody(String url) {
        String html = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (html == null || html.isBlank()) {
            return new ArticleExtractionResult(null, List.of(), false, OffsetDateTime.now(), "empty-html");
        }

        ExtractionResult extracted = extract(html);
        return new ArticleExtractionResult(
                extracted.body(),
                extracted.paragraphs(),
                extracted.lowConfidence(),
                extracted.fetchedAt(),
                extracted.extractionMeta()
        );
    }

    private ExtractionResult extract(String html) {
        Document document = Jsoup.parse(html);
        removeGlobalNoise(document);

        List<CandidateScore> scoredCandidates = collectCandidateScores(document);
        if (!scoredCandidates.isEmpty() && scoredCandidates.get(0).score() > 0) {
            CandidateScore best = scoredCandidates.get(0);
            double scoreGap = scoredCandidates.size() > 1 ? best.score() - scoredCandidates.get(1).score() : best.score();
            List<String> paragraphs = extractParagraphsFromCandidate(best.element());
            if (!paragraphs.isEmpty()) {
                String body = joinParagraphs(paragraphs);
                return new ExtractionResult(
                        paragraphs,
                        body,
                        isLowConfidence(paragraphs, body, false, best.linkDensity(), scoreGap),
                        OffsetDateTime.now(),
                        "dom-scored"
                );
            }
        }

        List<String> jsonLdParagraphs = extractJsonLdParagraphs(document);
        if (!jsonLdParagraphs.isEmpty()) {
            String body = joinParagraphs(jsonLdParagraphs);
            return new ExtractionResult(
                    jsonLdParagraphs,
                    body,
                    isLowConfidence(jsonLdParagraphs, body, true, 0.0d, 0.0d),
                    OffsetDateTime.now(),
                    "json-ld"
            );
        }

        List<String> metaParagraphs = extractMetaDescriptionParagraphs(document);
        if (!metaParagraphs.isEmpty()) {
            String body = joinParagraphs(metaParagraphs);
            return new ExtractionResult(
                    metaParagraphs,
                    body,
                    isLowConfidence(metaParagraphs, body, true, 0.0d, 0.0d),
                    OffsetDateTime.now(),
                    "meta-description"
            );
        }

        List<String> fallbackParagraphs = extractFallbackParagraphs(document.body());
        if (!fallbackParagraphs.isEmpty()) {
            String body = joinParagraphs(fallbackParagraphs);
            return new ExtractionResult(
                    fallbackParagraphs,
                    body,
                    isLowConfidence(fallbackParagraphs, body, true, 1.0d, 0.0d),
                    OffsetDateTime.now(),
                    "body-fallback"
            );
        }

        return new ExtractionResult(List.of(), null, true, OffsetDateTime.now(), "no-content");
    }

    private void removeGlobalNoise(Document document) {
        document.select("script:not([type=application/ld+json]),style,noscript,iframe,svg,form,nav,header,footer,aside").remove();
        removeHintedNoise(document.body(), false);
    }

    private List<CandidateScore> collectCandidateScores(Document document) {
        Map<String, Element> uniqueCandidates = new LinkedHashMap<>();
        for (String selector : CANDIDATE_SELECTORS) {
            Elements elements = document.select(selector);
            for (Element element : elements) {
                uniqueCandidates.putIfAbsent(element.cssSelector(), element);
            }
        }

        return uniqueCandidates.values().stream()
                .map(this::scoreCandidate)
                .sorted(Comparator.comparingDouble(CandidateScore::score).reversed())
                .toList();
    }

    private CandidateScore scoreCandidate(Element candidate) {
        Element clone = candidate.clone();
        removeSecondaryNoise(clone);

        List<String> paragraphs = extractParagraphsFromCandidate(clone);
        String text = normalizeText(clone.text());
        int paragraphCount = paragraphs.size();
        int longParagraphCount = (int) paragraphs.stream().filter(paragraph -> paragraph.length() >= LONG_PARAGRAPH_LENGTH).count();
        double linkDensity = computeLinkDensity(clone);
        double boilerplatePenalty = computeBoilerplatePenalty(clone, paragraphs);
        int listItemCount = clone.select("ul li, ol li").size();

        double score = 0.0d;
        score += paragraphCount * 4.0d;
        score += Math.min(text.length(), 4000) / 40.0d;
        score += longParagraphCount * 6.0d;
        score += computeContainerBonus(clone);
        score -= linkDensity * 40.0d;
        score -= boilerplatePenalty * 18.0d;
        if (listItemCount > paragraphCount * 2) {
            score -= 8.0d;
        }
        if (paragraphCount == 0 && text.length() < 200) {
            score -= 12.0d;
        }
        return new CandidateScore(candidate, score, linkDensity);
    }

    private int computeContainerBonus(Element candidate) {
        int bonus = 0;
        String hintSource = identifierHint(candidate);
        for (String hint : BODY_LIKE_HINTS) {
            if (hintSource.contains(hint)) {
                bonus += 4;
            }
        }
        if ("article".equals(candidate.tagName()) || "main".equals(candidate.tagName())) {
            bonus += 6;
        }
        return bonus;
    }

    private double computeLinkDensity(Element candidate) {
        String text = normalizeText(candidate.text());
        if (text.isBlank()) {
            return 1.0d;
        }
        String linkText = normalizeText(candidate.select("a").text());
        return Math.min(1.0d, (double) linkText.length() / (double) text.length());
    }

    private double computeBoilerplatePenalty(Element candidate, List<String> paragraphs) {
        String text = normalizeText(candidate.text()).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return 1.0d;
        }

        int hits = 0;
        for (String keyword : BOILERPLATE_KEYWORDS) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        double paragraphPenalty = paragraphs.isEmpty() ? 0.5d : 0.0d;
        return Math.min(1.0d, (hits / 6.0d) + paragraphPenalty);
    }

    private void removeSecondaryNoise(Element root) {
        if (root == null) {
            return;
        }
        root.select("aside,figure").remove();
        removeHintedNoise(root, true);
    }

    private void removeHintedNoise(Element root, boolean aggressive) {
        if (root == null) {
            return;
        }
        List<Element> elements = new ArrayList<>(root.getAllElements());
        for (Element element : elements) {
            if (element == root) {
                continue;
            }
            if (shouldRemoveByHint(element, aggressive)) {
                element.remove();
            }
        }
    }

    private boolean shouldRemoveByHint(Element element, boolean aggressive) {
        String hintSource = identifierHint(element);
        if (hintSource.isBlank()) {
            return false;
        }
        for (String hint : REMOVABLE_NODE_HINTS) {
            if (hintSource.contains(hint)) {
                if (!aggressive && looksLikeBodyContainer(element)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeBodyContainer(Element element) {
        String hintSource = identifierHint(element);
        for (String hint : BODY_LIKE_HINTS) {
            if (hintSource.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractParagraphsFromCandidate(Element candidate) {
        if (candidate == null) {
            return List.of();
        }

        Element working = candidate.clone();
        removeSecondaryNoise(working);

        List<String> paragraphs = normalizeParagraphs(working.select("p").stream()
                .map(Element::text)
                .toList());
        if (paragraphs.size() >= 2) {
            return paragraphs;
        }
        if (!paragraphs.isEmpty()) {
            return paragraphs;
        }

        List<String> shortParagraphs = normalizeParagraphs(working.select("p").stream()
                .map(Element::text)
                .toList(), 8);
        if (!shortParagraphs.isEmpty()) {
            return shortParagraphs;
        }

        List<String> divParagraphs = normalizeParagraphs(working.select("div").stream()
                .map(Element::text)
                .toList());
        if (!divParagraphs.isEmpty()) {
            return divParagraphs;
        }

        return extractFallbackParagraphs(working);
    }

    private List<String> extractJsonLdParagraphs(Document document) {
        List<String> results = new ArrayList<>();
        for (Element script : document.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }

            String articleBody = extractJsonField(json, "articleBody");
            if (articleBody != null && !articleBody.isBlank()) {
                results.add(articleBody);
                continue;
            }

            String description = extractJsonField(json, "description");
            if (description != null && !description.isBlank()) {
                results.add(description);
            }
        }
        return normalizeParagraphs(results);
    }

    private List<String> extractMetaDescriptionParagraphs(Document document) {
        List<String> results = new ArrayList<>();
        Element ogDescription = document.selectFirst("meta[property=og:description]");
        if (ogDescription != null) {
            results.add(ogDescription.attr("content"));
        }
        Element description = document.selectFirst("meta[name=description]");
        if (description != null) {
            results.add(description.attr("content"));
        }
        return normalizeParagraphs(results);
    }

    private List<String> extractFallbackParagraphs(Element root) {
        if (root == null) {
            return List.of();
        }

        List<String> rawBlocks = new ArrayList<>();
        for (TextNode textNode : root.textNodes()) {
            rawBlocks.add(textNode.text());
        }
        if (rawBlocks.isEmpty()) {
            rawBlocks.add(root.text());
        }
        return normalizeParagraphs(splitTextBlocks(rawBlocks));
    }

    private List<String> splitTextBlocks(List<String> rawBlocks) {
        List<String> paragraphs = new ArrayList<>();
        for (String rawBlock : rawBlocks) {
            if (rawBlock == null || rawBlock.isBlank()) {
                continue;
            }
            String[] split = rawBlock.split("(?:\\n\\s*\\n+)|(?<=[.!?]|다\\.)\\s+");
            for (String piece : split) {
                paragraphs.add(piece);
            }
        }
        return paragraphs;
    }

    private List<String> normalizeParagraphs(List<String> rawParagraphs) {
        return normalizeParagraphs(rawParagraphs, MIN_PARAGRAPH_LENGTH);
    }

    private List<String> normalizeParagraphs(List<String> rawParagraphs, int minLength) {
        Set<String> unique = new LinkedHashSet<>();
        for (String rawParagraph : rawParagraphs) {
            String normalized = normalizeText(rawParagraph);
            if (!isMeaningfulParagraph(normalized, minLength)) {
                continue;
            }
            unique.add(normalized);
        }
        return new ArrayList<>(unique);
    }

    private boolean isMeaningfulParagraph(String paragraph) {
        return isMeaningfulParagraph(paragraph, MIN_PARAGRAPH_LENGTH);
    }

    private boolean isMeaningfulParagraph(String paragraph, int minLength) {
        if (paragraph == null || paragraph.isBlank()) {
            return false;
        }
        if (paragraph.length() < minLength) {
            return false;
        }

        String normalized = paragraph.toLowerCase(Locale.ROOT);
        int hitCount = 0;
        for (String keyword : NOISE_PARAGRAPH_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                hitCount++;
            }
        }
        return hitCount < 2;
    }

    private boolean isLowConfidence(
            List<String> paragraphs,
            String body,
            boolean fallbackUsed,
            double linkDensity,
            double scoreGap
    ) {
        if (body == null || body.isBlank()) {
            return true;
        }

        return fallbackUsed
                || paragraphs.size() < 2
                || body.length() < LOW_CONFIDENCE_LENGTH_THRESHOLD
                || linkDensity >= HIGH_LINK_DENSITY
                || scoreGap < CLOSE_SCORE_GAP;
    }

    private String joinParagraphs(List<String> paragraphs) {
        return paragraphs.stream()
                .map(String::trim)
                .filter(paragraph -> !paragraph.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private String identifierHint(Element element) {
        StringBuilder builder = new StringBuilder();
        if (element.id() != null) {
            builder.append(element.id()).append(' ');
        }
        if (element.className() != null) {
            builder.append(element.className()).append(' ');
        }
        builder.append(element.tagName());
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private String extractJsonField(String json, String fieldName) {
        String quotedField = "\"" + fieldName + "\"";
        int index = json.indexOf(quotedField);
        if (index < 0) {
            return null;
        }

        int colonIndex = json.indexOf(':', index + quotedField.length());
        if (colonIndex < 0) {
            return null;
        }

        int firstQuote = json.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaping) {
                builder.append(current == 'n' ? '\n' : current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                break;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }

    public record ArticleExtractionResult(
            String body,
            List<String> paragraphs,
            boolean lowConfidence,
            OffsetDateTime fetchedAt,
            String extractionMeta
    ) {
        public ArticleExtractionResult(String body, boolean lowConfidence) {
            this(
                    body,
                    body == null || body.isBlank() ? List.of() : List.of(body),
                    lowConfidence,
                    OffsetDateTime.now(),
                    "legacy"
            );
        }
    }

    private record ExtractionResult(
            List<String> paragraphs,
            String body,
            boolean lowConfidence,
            OffsetDateTime fetchedAt,
            String extractionMeta
    ) {
    }

    private record CandidateScore(
            Element element,
            double score,
            double linkDensity
    ) {
    }
}
