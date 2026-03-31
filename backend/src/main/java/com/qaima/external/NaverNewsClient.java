package com.qaima.external;

import com.qaima.external.dto.news.NaverNewsSearchResponse;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class NaverNewsClient {

    private static final String FALLBACK_PUBLISHER = "NAVER_NEWS";
    private static final String DEFAULT_SORT = "date";
    private static final DateTimeFormatter NAVER_PUB_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final WebClient webClient;

    @Value("${news.naver.base-url:https://openapi.naver.com/v1/search/news.json}")
    private String baseUrl;

    @Value("${news.naver.client-id:${NAVER_CLIENT_ID:}}")
    private String clientId;

    @Value("${news.naver.client-secret:${NAVER_CLIENT_SECRET:}}")
    private String clientSecret;

    public NaverNewsClient(@Qualifier("defaultWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public NaverNewsSearchResult search(String query, int display, int start) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("NAVER_NEWS_API_KEY_MISSING");
        }

        if (query == null || query.isBlank()) {
            return new NaverNewsSearchResult(List.of(), List.of("NEWS_LIST_FETCH_FAILED"));
        }

        NaverNewsSearchResponse response = webClient.get()
                .uri(baseUrl + "?query={query}&display={display}&start={start}&sort={sort}",
                        query, display, start, DEFAULT_SORT)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .bodyToMono(NaverNewsSearchResponse.class)
                .block();

        if (response == null || response.getItems() == null) {
            return new NaverNewsSearchResult(List.of(), List.of());
        }

        List<String> warnings = new ArrayList<>();
        List<NaverNewsArticle> articles = new ArrayList<>();
        response.getItems().forEach(item -> {
            String title = stripHtml(item.getTitle());
            String summary = stripHtml(item.getDescription());
            String url = resolveUrl(item);

            if (title == null || title.isBlank() || url == null || url.isBlank()) {
                warnings.add("NEWS_INVALID_ITEM_SKIPPED");
                return;
            }

            OffsetDateTime publishedAt = parsePublishedAt(item.getPubDate());
            if (publishedAt == null) {
                warnings.add("NEWS_PUBDATE_PARSE_FAILED");
                warnings.add("NEWS_INVALID_ITEM_SKIPPED");
                return;
            }

            articles.add(NaverNewsArticle.builder()
                    .title(title)
                    .url(url)
                    .publisher(resolvePublisher(item))
                    .publishedAt(publishedAt)
                    .summary(summary)
                    .build());
        });

        return new NaverNewsSearchResult(articles, dedupe(warnings));
    }

    private OffsetDateTime parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, NAVER_PUB_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]*>", "").replace("&quot;", "\"").replace("&amp;", "&").trim();
    }

    private String resolveUrl(NaverNewsSearchResponse.NaverNewsItemDto item) {
        if (item.getOriginalLink() != null && !item.getOriginalLink().isBlank()) {
            return item.getOriginalLink();
        }
        return item.getLink();
    }

    private String resolvePublisher(NaverNewsSearchResponse.NaverNewsItemDto item) {
        String url = resolveUrl(item);
        if (url == null || url.isBlank()) {
            return FALLBACK_PUBLISHER;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return FALLBACK_PUBLISHER;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.startsWith("www.")) {
                normalizedHost = normalizedHost.substring(4);
            }
            return normalizedHost;
        } catch (Exception ex) {
            return FALLBACK_PUBLISHER;
        }
    }

    private List<String> dedupe(List<String> warnings) {
        List<String> deduped = new ArrayList<>();
        for (String warning : warnings) {
            if (warning != null && !warning.isBlank() && !deduped.contains(warning)) {
                deduped.add(warning);
            }
        }
        return deduped;
    }

    @Getter
    @Builder
    public static class NaverNewsArticle {
        private final String title;
        private final String url;
        private final String publisher;
        private final OffsetDateTime publishedAt;
        private final String summary;
    }

    @Getter
    @AllArgsConstructor
    public static class NaverNewsSearchResult {
        private final List<NaverNewsArticle> articles;
        private final List<String> warnings;
    }
}
