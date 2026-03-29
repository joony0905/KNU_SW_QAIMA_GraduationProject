package com.qaima.service.feature2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.News;
import com.qaima.domain.NewsSecurityMap;
import com.qaima.domain.NewsSecurityMapId;
import com.qaima.domain.SentimentResult;
import com.qaima.domain.SentimentResultId;
import com.qaima.domain.Stock;
import com.qaima.dto.news.NewsDetailDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.external.Feature2NewsSentimentClient;
import com.qaima.external.NaverNewsClient;
import com.qaima.external.NewsArticleExtractorClient;
import com.qaima.repository.NewsRepository;
import com.qaima.repository.NewsSecurityMapRepository;
import com.qaima.repository.SentimentResultRepository;
import com.qaima.service.feature2.model.NewsSentimentInput;
import com.qaima.service.feature2.model.NewsSentimentResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class NewsSentimentService {

    private static final String NEWS_LIST_KEY = "feat2:news:list:stock:%s:v1";
    private static final String NEWS_FOCUS_KEY = "feat2:news:focus:urlhash:%s:v1";
    private static final String NEWS_SENTIMENT_KEY = "feat2:news:sentiment:news:%s:model:%s:v1";
    private static final String NEWS_DETAIL_KEY = "feat2:news:detail:news:%s:v1";
    private static final Duration NEWS_LIST_TTL = Duration.ofMinutes(10);
    private static final Duration NEWS_FOCUS_TTL = Duration.ofHours(24);
    private static final Duration NEWS_SENTIMENT_TTL = Duration.ofDays(7);
    private static final Duration NEWS_DETAIL_TTL = Duration.ofHours(1);
    private static final int NEWS_FETCH_LIMIT = 10;
    private static final int NEWS_FETCH_START = 1;
    private static final int NEWS_FETCH_STEP = 10;
    private static final int NEWS_FETCH_MAX_START = 51;
    private static final List<String> EXTERNAL_FACTOR_KEYWORDS = List.of(
            "주식", "증시", "증권", "투자", "수급", "밸류에이션", "목표주가",
            "실적", "실적발표", "매출", "매출액", "영업이익", "순이익",
            "금리", "환율", "채권", "자금", "유동성", "인플레이션", "경기",
            "반도체", "배터리", "ai", "메모리", "공급망", "수요", "공급",
            "생산", "공장", "설비", "capex", "수출", "수입", "점유율",
            "경쟁사", "고객사", "산업", "업황", "수주", "계약", "납품",
            "규제", "제재", "정책", "관세", "보조금", "기준금리", "통화정책",
            "행정처분", "조사", "공정위", "금융위", "금감원", "특허", "소송",
            "사고", "화재", "리콜", "셧다운", "중단", "결함", "장애",
            "분쟁", "압수수색", "해킹", "유출", "파업", "리스크", "위기",
            "인수", "합병", "매각", "상장폐지", "내부자거래", "불확실성"
    );

    private final NewsRepository newsRepository;
    private final NewsSecurityMapRepository newsSecurityMapRepository;
    private final SentimentResultRepository sentimentResultRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NaverNewsClient naverNewsClient;
    private final NewsArticleExtractorClient articleExtractorClient;
    private final Feature2NewsSentimentClient sentimentClient;

    @Value("${feature2.news.sentiment.model:gpt-4o-mini}")
    private String sentimentModel;

    public Mono<NewsLoadResult> loadNews(Stock stock) {
        return Mono.fromCallable(() -> loadNewsBlocking(stock))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NewsDetailDto> loadNewsDetail(Long newsId) {
        return Mono.fromCallable(() -> loadNewsDetailBlocking(newsId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private NewsLoadResult loadNewsBlocking(Stock stock) {
        List<String> warnings = new ArrayList<>();
        if (stock == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return NewsLoadResult.builder().newsList(List.of()).warnings(warnings).build();
        }
        if (stock.getCompanyName() == null || stock.getCompanyName().isBlank()) {
            warnings.add("NEWS_LIST_FETCH_FAILED");
            return NewsLoadResult.builder().newsList(List.of()).warnings(dedupeWarnings(warnings)).build();
        }

        String listKey = NEWS_LIST_KEY.formatted(stock.getStockCode());
        List<NewsItemDto> cached = readCacheList(listKey, warnings);
        if (cached != null) {
            return NewsLoadResult.builder().newsList(cached).warnings(dedupeWarnings(warnings)).build();
        }

        try {
            List<NaverNewsClient.NaverNewsArticle> articles = collectRelevantArticles(stock.getCompanyName(), warnings);
            upsertNewsMeta(stock, articles, warnings);
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] news list fetch failed. stockCode={}", stock.getStockCode(), ex);
            warnings.add("NEWS_LIST_FETCH_FAILED");
        }

        List<News> latestNews = newsSecurityMapRepository.findLatestNewsByStockId(
                stock.getStockId(),
                PageRequest.of(0, NEWS_FETCH_LIMIT)
        );

        List<NewsItemDto> newsList = buildNewsList(latestNews, warnings);
        writeCacheList(listKey, newsList, warnings);

        return NewsLoadResult.builder()
                .newsList(newsList)
                .warnings(dedupeWarnings(warnings))
                .build();
    }

    private NewsDetailDto loadNewsDetailBlocking(Long newsId) {
        if (newsId == null) {
            throw new ResourceNotFoundException("NEWS_DETAIL_NOT_FOUND");
        }

        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new ResourceNotFoundException("NEWS_DETAIL_NOT_FOUND"));
        List<String> warnings = new ArrayList<>();

        CachedNewsDetail cachedDetail = readDetailCache(newsId, warnings);
        BigDecimal sentimentScore = resolveExistingSentimentScore(news, warnings);

        if (cachedDetail != null) {
            if (cachedDetail.isLowConfidence()) {
                warnings.add("NEWS_BODY_LOW_CONFIDENCE:" + newsId);
            }
            return NewsDetailDto.builder()
                    .newsId(news.getNewsId())
                    .title(news.getTitle())
                    .url(news.getUrl())
                    .publisher(news.getSource())
                    .publishedAt(news.getPublishedAt())
                    .body(cachedDetail.getBody())
                    .readerSummary(cachedDetail.getReaderSummary())
                    .sentimentScore(sentimentScore)
                    .warnings(dedupeWarnings(warnings))
                    .build();
        }

        try {
            CachedNewsDetail extractedDetail = fetchAndCacheDetail(news, warnings);
            if (extractedDetail == null || extractedDetail.getBody() == null || extractedDetail.getBody().isBlank()) {
                warnings.add("NEWS_DETAIL_FETCH_FAILED:" + newsId);
                return buildFallbackDetail(news, sentimentScore, warnings);
            }

            if (extractedDetail.isLowConfidence()) {
                warnings.add("NEWS_BODY_LOW_CONFIDENCE:" + newsId);
            }
            return NewsDetailDto.builder()
                    .newsId(news.getNewsId())
                    .title(news.getTitle())
                    .url(news.getUrl())
                    .publisher(news.getSource())
                    .publishedAt(news.getPublishedAt())
                    .body(extractedDetail.getBody())
                    .readerSummary(extractedDetail.getReaderSummary())
                    .sentimentScore(sentimentScore)
                    .warnings(dedupeWarnings(warnings))
                    .build();
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] detail fetch failed. newsId={}", newsId, ex);
            warnings.add("NEWS_DETAIL_FETCH_FAILED:" + newsId);
            return buildFallbackDetail(news, sentimentScore, warnings);
        }
    }

    @Transactional
    protected void upsertNewsMeta(Stock stock, List<NaverNewsClient.NaverNewsArticle> articles, List<String> warnings) {
        for (NaverNewsClient.NaverNewsArticle article : articles) {
            try {
                News news = newsRepository.findByUrl(article.getUrl()).orElseGet(News::new);
                news.setTitle(article.getTitle());
                news.setUrl(article.getUrl());
                news.setPublishedAt(article.getPublishedAt() != null ? article.getPublishedAt() : OffsetDateTime.now());
                news.setSource(article.getPublisher());
                news.setSummary(article.getSummary());
                News saved = newsRepository.save(news);

                NewsSecurityMapId id = new NewsSecurityMapId(saved.getNewsId(), stock.getStockId());
                if (!newsSecurityMapRepository.existsById(id)) {
                    NewsSecurityMap map = new NewsSecurityMap();
                    map.setId(id);
                    map.setNews(saved);
                    map.setStock(stock);
                    newsSecurityMapRepository.save(map);
                }
            } catch (Exception ex) {
                log.warn("[NewsSentimentService] news meta upsert failed. url={}", article.getUrl(), ex);
                warnings.add("NEWS_META_UPSERT_FAILED");
            }
        }
    }

    private List<NaverNewsClient.NaverNewsArticle> collectRelevantArticles(String companyName, List<String> warnings) {
        List<NaverNewsClient.NaverNewsArticle> result = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        boolean expandedFetch = false;

        warnings.add("NEWS_FILTER_APPLIED");

        for (int start = NEWS_FETCH_START; result.size() < NEWS_FETCH_LIMIT && start <= NEWS_FETCH_MAX_START; start += NEWS_FETCH_STEP) {
            if (start > NEWS_FETCH_START) {
                expandedFetch = true;
            }

            NaverNewsClient.NaverNewsSearchResult searchResult =
                    naverNewsClient.search(companyName, NEWS_FETCH_LIMIT, start);
            warnings.addAll(searchResult.getWarnings());

            List<NaverNewsClient.NaverNewsArticle> filtered = searchResult.getArticles().stream()
                    .filter(article -> isRelevantExternalFactorNews(article.getTitle(), article.getSummary()))
                    .toList();

            for (NaverNewsClient.NaverNewsArticle article : filtered) {
                if (article.getUrl() != null && seenUrls.add(article.getUrl())) {
                    result.add(article);
                    if (result.size() >= NEWS_FETCH_LIMIT) {
                        break;
                    }
                }
            }
        }

        if (expandedFetch) {
            warnings.add("NEWS_FILTER_EXPANDED_FETCH");
        }
        if (result.size() < NEWS_FETCH_LIMIT) {
            warnings.add("NEWS_FILTER_INSUFFICIENT_RESULT");
        }
        return result;
    }

    private NewsDetailDto buildFallbackDetail(News news, BigDecimal sentimentScore, List<String> warnings) {
        return NewsDetailDto.builder()
                .newsId(news.getNewsId())
                .title(news.getTitle())
                .url(news.getUrl())
                .publisher(news.getSource())
                .publishedAt(news.getPublishedAt())
                .body(null)
                .readerSummary(null)
                .sentimentScore(sentimentScore)
                .warnings(dedupeWarnings(warnings))
                .build();
    }

    boolean isRelevantExternalFactorNews(String title, String summary) {
        int score = 0;
        if (containsAny(summary, EXTERNAL_FACTOR_KEYWORDS)) {
            score += 2;
        }
        if (containsAny(title, EXTERNAL_FACTOR_KEYWORDS)) {
            score += 1;
        }
        return score >= 1;
    }

    private boolean containsAny(String value, List<String> keywords) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase();
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<NewsItemDto> buildNewsList(List<News> latestNews, List<String> warnings) {
        if (latestNews == null || latestNews.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> sentimentScores = resolveSentimentScores(latestNews, warnings);
        return latestNews.stream()
                .sorted(Comparator.comparing(News::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(news -> isRenderableNews(news, warnings))
                .map(news -> NewsItemDto.builder()
                        .newsId(news.getNewsId())
                        .title(news.getTitle())
                        .url(news.getUrl())
                        .publisher(news.getSource())
                        .publishedAt(news.getPublishedAt() != null ? news.getPublishedAt() : OffsetDateTime.now())
                        .summary(news.getSummary())
                        .sentimentScore(sentimentScores.get(news.getUrl()))
                        .build())
                .toList();
    }

    private Map<String, BigDecimal> resolveSentimentScores(List<News> latestNews, List<String> warnings) {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        List<NewsSentimentInput> toAnalyze = new ArrayList<>();
        Map<String, News> pendingNews = new LinkedHashMap<>();

        for (News news : latestNews) {
            if (!isSentimentEligible(news)) {
                continue;
            }

            BigDecimal cached = readSentimentCache(news.getNewsId(), warnings);
            if (cached != null) {
                scores.put(news.getUrl(), cached);
                continue;
            }

            Optional<SentimentResult> existing = sentimentResultRepository.findById(
                    new SentimentResultId(news.getNewsId(), sentimentModel)
            );
            if (existing.isPresent()) {
                BigDecimal score = existing.get().getScore();
                scores.put(news.getUrl(), score);
                writeSentimentCache(news.getNewsId(), score, warnings);
                continue;
            }

            String focusText = resolveFocusText(news, warnings);
            if (focusText == null || focusText.isBlank()) {
                warnings.add("NEWS_BODY_FETCH_FAILED:" + hashUrl(news.getUrl()));
                continue;
            }

            toAnalyze.add(new NewsSentimentInput(
                    news.getUrl(),
                    news.getTitle(),
                    news.getSource(),
                    news.getPublishedAt(),
                    focusText
            ));
            pendingNews.put(news.getUrl(), news);
        }

        if (!toAnalyze.isEmpty()) {
            try {
                Feature2NewsSentimentClient.SentimentBatchResponse response =
                        sentimentClient.analyze(toAnalyze, sentimentModel);
                warnings.addAll(response.warnings());
                for (String invalidScoreUrl : response.invalidScoreUrls()) {
                    News invalidScoreNews = pendingNews.get(invalidScoreUrl);
                    if (invalidScoreNews != null && invalidScoreNews.getNewsId() != null) {
                        warnings.add("NEWS_SENTIMENT_INVALID_SCORE:" + invalidScoreNews.getNewsId());
                    }
                }

                List<String> resolvedUrls = new ArrayList<>();
                for (NewsSentimentResult result : response.results()) {
                    News matchedNews = pendingNews.get(result.url());
                    if (matchedNews == null || matchedNews.getNewsId() == null) {
                        warnings.add("NEWS_SENTIMENT_INVALID_RESPONSE");
                        continue;
                    }
                    if (result.sentimentScore() == null) {
                        warnings.add("NEWS_SENTIMENT_INVALID_SCORE:" + matchedNews.getNewsId());
                        continue;
                    }
                    resolvedUrls.add(result.url());
                    scores.put(result.url(), result.sentimentScore());
                    writeSentimentCache(matchedNews.getNewsId(), result.sentimentScore(), warnings);
                    upsertSentimentResult(matchedNews, result.sentimentScore(), warnings);
                }

                for (Map.Entry<String, News> entry : pendingNews.entrySet()) {
                    News news = entry.getValue();
                    if (news == null || news.getNewsId() == null) {
                        continue;
                    }
                    if (!resolvedUrls.contains(entry.getKey()) && !response.invalidScoreUrls().contains(entry.getKey())) {
                        warnings.add("NEWS_SENTIMENT_FAILED:" + news.getNewsId());
                    }
                }
            } catch (Exception ex) {
                log.warn("[NewsSentimentService] sentiment analyze failed", ex);
                for (NewsSentimentInput input : toAnalyze) {
                    News failedNews = pendingNews.get(input.url());
                    if (failedNews != null && failedNews.getNewsId() != null) {
                        warnings.add("NEWS_SENTIMENT_FAILED:" + failedNews.getNewsId());
                    }
                }
            }
        }

        return scores;
    }

    private BigDecimal resolveExistingSentimentScore(News news, List<String> warnings) {
        BigDecimal cached = readSentimentCache(news.getNewsId(), warnings);
        if (cached != null) {
            return cached;
        }
        return sentimentResultRepository.findById(new SentimentResultId(news.getNewsId(), sentimentModel))
                .map(SentimentResult::getScore)
                .orElse(null);
    }

    @Transactional
    protected void upsertSentimentResult(News news, BigDecimal score, List<String> warnings) {
        if (news == null || score == null) {
            return;
        }
        try {
            SentimentResultId id = new SentimentResultId(news.getNewsId(), sentimentModel);
            SentimentResult result = sentimentResultRepository.findById(id).orElseGet(SentimentResult::new);
            result.setId(id);
            result.setNews(news);
            result.setScore(score);
            result.setLabel(null);
            result.setCreatedAt(OffsetDateTime.now());
            sentimentResultRepository.save(result);
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] sentiment upsert failed. newsId={}", news.getNewsId(), ex);
            warnings.add("NEWS_SENTIMENT_FAILED:" + news.getNewsId());
        }
    }

    private String resolveFocusText(News news, List<String> warnings) {
        String hash = hashUrl(news.getUrl());
        String cachedFocus = readStringCache(NEWS_FOCUS_KEY.formatted(hash), warnings);
        if (cachedFocus != null && !cachedFocus.isBlank()) {
            return cachedFocus;
        }

        CachedNewsDetail detail = readDetailCache(news.getNewsId(), warnings);
        if (detail == null || detail.getBody() == null || detail.getBody().isBlank()) {
            try {
                detail = fetchAndCacheDetail(news, warnings);
            } catch (Exception ex) {
                log.warn("[NewsSentimentService] body fetch failed. url={}", news.getUrl(), ex);
                warnings.add("NEWS_BODY_FETCH_FAILED:" + hash);
                return null;
            }
        }

        if (detail == null || detail.getBody() == null || detail.getBody().isBlank()) {
            warnings.add("NEWS_BODY_FETCH_FAILED:" + hash);
            return null;
        }
        if (detail.isLowConfidence()) {
            warnings.add("NEWS_BODY_LOW_CONFIDENCE:" + news.getNewsId());
        }

        String body = detail.getBody();
        String focusText = buildFocusText(news, body);
        if (focusText != null && !focusText.isBlank()) {
            writeStringCache(NEWS_FOCUS_KEY.formatted(hash), focusText, NEWS_FOCUS_TTL, warnings);
        }
        return focusText;
    }

    private String buildFocusText(News news, String body) {
        StringBuilder sb = new StringBuilder();
        if (news.getTitle() != null && !news.getTitle().isBlank()) {
            sb.append(news.getTitle()).append('\n');
        }
        if (news.getSummary() != null && !news.getSummary().isBlank()) {
            sb.append(news.getSummary()).append('\n');
        }
        if (body != null && !body.isBlank()) {
            sb.append(body, 0, Math.min(body.length(), 1200));
        }
        String text = sb.toString().trim();
        return text.isBlank() ? null : text;
    }

    private String normalizeBody(String body) {
        if (body == null) {
            return null;
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String buildReaderSummary(String body) {
        String normalized = normalizeBody(body);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 320) {
            return normalized;
        }

        String[] sentences = normalized.split("(?<=[.!?]|다\\.)\\s+");
        StringBuilder summary = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(sentence.trim());
            if (summary.length() >= 320 || countSummarySentences(summary.toString()) >= 3) {
                break;
            }
        }

        String compact = summary.toString().trim();
        if (compact.isBlank()) {
            return normalized.substring(0, Math.min(normalized.length(), 320));
        }
        return compact.length() > 320 ? compact.substring(0, 320).trim() : compact;
    }

    private int countSummarySentences(String summary) {
        return summary.split("(?<=[.!?]|다\\.)\\s+").length;
    }

    private CachedNewsDetail fetchAndCacheDetail(News news, List<String> warnings) {
        NewsArticleExtractorClient.ArticleExtractionResult extraction = articleExtractorClient.fetchArticleBody(news.getUrl());
        String body = normalizeBody(extraction.body());
        if (body == null || body.isBlank()) {
            return null;
        }

        CachedNewsDetail detail = CachedNewsDetail.builder()
                .newsId(news.getNewsId())
                .title(news.getTitle())
                .url(news.getUrl())
                .publisher(news.getSource())
                .publishedAt(news.getPublishedAt())
                .body(body)
                .readerSummary(buildReaderSummary(body))
                .fetchedAt(OffsetDateTime.now())
                .lowConfidence(extraction.lowConfidence())
                .build();
        writeDetailCache(detail, warnings);
        return detail;
    }

    private List<NewsItemDto> readCacheList(String key, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue().get(key).block();
            if (value == null || value.isBlank()) {
                return null;
            }
            List<CachedNewsItem> cached = objectMapper.readValue(value, new TypeReference<List<CachedNewsItem>>() {});
            return cached.stream()
                    .map(item -> NewsItemDto.builder()
                            .newsId(item.getNewsId())
                            .title(item.getTitle())
                            .url(item.getUrl())
                            .publisher(item.getPublisher())
                            .publishedAt(item.getPublishedAt())
                            .summary(item.getSummary())
                            .sentimentScore(item.getSentimentScore())
                            .build())
                    .toList();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_READ_FAILED");
            return null;
        }
    }

    private void writeCacheList(String key, List<NewsItemDto> newsList, List<String> warnings) {
        try {
            List<CachedNewsItem> cached = newsList.stream()
                    .map(item -> CachedNewsItem.builder()
                            .newsId(item.getNewsId())
                            .title(item.getTitle())
                            .url(item.getUrl())
                            .publisher(item.getPublisher())
                            .publishedAt(item.getPublishedAt())
                            .summary(item.getSummary())
                            .sentimentScore(item.getSentimentScore())
                            .build())
                    .toList();
            redisTemplate.opsForValue()
                    .set(key, objectMapper.writeValueAsString(cached), NEWS_LIST_TTL)
                    .block();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_WRITE_FAILED");
        }
    }

    private CachedNewsDetail readDetailCache(Long newsId, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue()
                    .get(NEWS_DETAIL_KEY.formatted(newsId))
                    .block();
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedNewsDetail.class);
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_READ_FAILED");
            return null;
        }
    }

    private void writeDetailCache(CachedNewsDetail detail, List<String> warnings) {
        try {
            redisTemplate.opsForValue()
                    .set(NEWS_DETAIL_KEY.formatted(detail.getNewsId()), objectMapper.writeValueAsString(detail), NEWS_DETAIL_TTL)
                    .block();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_WRITE_FAILED");
        }
    }

    private BigDecimal readSentimentCache(Long newsId, List<String> warnings) {
        if (newsId == null) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue()
                    .get(NEWS_SENTIMENT_KEY.formatted(newsId, sentimentModel))
                    .block();
            if (value == null || value.isBlank()) {
                return null;
            }
            CachedSentimentValue cached = objectMapper.readValue(value, CachedSentimentValue.class);
            return cached.getSentimentScore();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_READ_FAILED");
            return null;
        }
    }

    private void writeSentimentCache(Long newsId, BigDecimal score, List<String> warnings) {
        if (newsId == null || score == null) {
            return;
        }
        try {
            CachedSentimentValue cached = new CachedSentimentValue(score, OffsetDateTime.now());
            redisTemplate.opsForValue()
                    .set(
                            NEWS_SENTIMENT_KEY.formatted(newsId, sentimentModel),
                            objectMapper.writeValueAsString(cached),
                            NEWS_SENTIMENT_TTL
                    )
                    .block();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_WRITE_FAILED");
        }
    }

    private String readStringCache(String key, List<String> warnings) {
        try {
            return redisTemplate.opsForValue().get(key).block();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_READ_FAILED");
            return null;
        }
    }

    private void writeStringCache(String key, String value, Duration ttl, List<String> warnings) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl).block();
        } catch (Exception ex) {
            warnings.add("NEWS_CACHE_WRITE_FAILED");
        }
    }

    private String hashUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((url == null ? "" : url).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return Integer.toHexString((url == null ? "" : url).hashCode());
        }
    }

    private List<String> dedupeWarnings(List<String> warnings) {
        List<String> deduped = new ArrayList<>();
        for (String warning : warnings) {
            if (warning != null && !warning.isBlank() && !deduped.contains(warning)) {
                deduped.add(warning);
            }
        }
        return deduped;
    }

    private boolean isRenderableNews(News news, List<String> warnings) {
        if (news == null) {
            return false;
        }
        if (news.getTitle() == null || news.getTitle().isBlank()) {
            warnings.add("NEWS_META_UPSERT_FAILED");
            return false;
        }
        if (news.getUrl() == null || news.getUrl().isBlank()) {
            warnings.add("NEWS_META_UPSERT_FAILED");
            return false;
        }
        return true;
    }

    private boolean isSentimentEligible(News news) {
        return news != null
                && news.getNewsId() != null
                && news.getUrl() != null
                && !news.getUrl().isBlank()
                && news.getTitle() != null
                && !news.getTitle().isBlank();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class CachedNewsItem {
        private Long newsId;
        private String title;
        private String url;
        private String publisher;
        private OffsetDateTime publishedAt;
        private String summary;
        private BigDecimal sentimentScore;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CachedSentimentValue {
        private BigDecimal sentimentScore;
        private OffsetDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class CachedNewsDetail {
        private Long newsId;
        private String title;
        private String url;
        private String publisher;
        private OffsetDateTime publishedAt;
        private String body;
        private String readerSummary;
        private OffsetDateTime fetchedAt;
        private boolean lowConfidence;
    }
}
