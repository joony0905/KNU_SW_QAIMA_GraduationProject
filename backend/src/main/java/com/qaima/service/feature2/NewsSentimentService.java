package com.qaima.service.feature2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.CompanyNameNormalizer;
import com.qaima.common.NewsWarningCodes;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.News;
import com.qaima.domain.NewsSecurityMap;
import com.qaima.domain.NewsSecurityMapId;
import com.qaima.domain.SentimentResult;
import com.qaima.domain.SentimentResultId;
import com.qaima.domain.Stock;
import com.qaima.domain.StockAlias;
import com.qaima.dto.news.NewsDetailDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.external.Feature2NewsSentimentClient;
import com.qaima.external.NaverNewsClient;
import com.qaima.external.NewsArticleExtractorClient;
import com.qaima.repository.NewsRepository;
import com.qaima.repository.NewsSecurityMapRepository;
import com.qaima.repository.SentimentResultRepository;
import com.qaima.repository.StockAliasRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.feature2.model.NewsSentimentInput;
import com.qaima.service.feature2.model.NewsSentimentObservationCommand;
import com.qaima.service.feature2.model.NewsSentimentResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
@Slf4j
public class NewsSentimentService {

    private static final String NEWS_LIST_KEY_PREFIX = "feat2:news:list:stock:";
    private static final String NEWS_REFRESH_KEY_PREFIX = "feat2:news:refresh:stock:";
    private static final String NEWS_DETAIL_KEY_PREFIX = "feat2:news:detail:news:";
    private static final String NEWS_FOCUS_KEY_PREFIX = "feat2:news:focus:news:";
    private static final String NEWS_SENTIMENT_KEY_PREFIX = "feat2:news:sentiment:news:";
    private static final Duration NEWS_LIST_TTL = Duration.ofMinutes(5);
    private static final Duration NEWS_REFRESH_TTL = Duration.ofMinutes(15);
    private static final Duration NEWS_DETAIL_TTL = Duration.ofDays(7);
    private static final Duration NEWS_FOCUS_TTL = Duration.ofDays(7);
    private static final Duration NEWS_SENTIMENT_TTL = Duration.ofDays(7);
    private static final Duration REDIS_BLOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final int NEWS_FETCH_LIMIT = 15;
    private static final int NEWS_EXPORT_DISPLAY_LIMIT = 100;
    private static final int NEWS_FETCH_START = 1;
    private static final int NEWS_FETCH_STEP = 10;
    private static final int NEWS_FETCH_MAX_START = 51;
    private static final int FOCUS_MAX_LENGTH = 240;
    private static final int DETAIL_MAX_LENGTH = 600;
    // v3 확장 지점: 로컬 모델 입력 포맷을 바꿔 실험할 때 이 버전을 함께 올려 관측 로그를 분리한다.
    private static final String NEWS_SENTIMENT_INPUT_FORMAT_VERSION = "focus_detail_fallback_v1";
    // 기존 캐시/유니크키 호환을 위해 prompt_version 컬럼명은 유지하되 로컬 입력 포맷 버전을 저장한다.
    private static final String NEWS_SENTIMENT_PROMPT_VERSION = NEWS_SENTIMENT_INPUT_FORMAT_VERSION;
    private static final List<String> EXTERNAL_FACTOR_KEYWORDS = List.of(
            "주식", "증시", "증권", "투자", "수급", "밸류에이션", "목표주가",
            "실적", "실적발표", "매출", "매출액", "영업이익", "순이익",
            "수익성", "마진", "원가", "가이던스", "컨센서스", "서프라이즈",
            "금리", "환율", "채권", "자금", "유동성", "인플레이션", "경기",
            "반도체", "배터리", "ai", "메모리", "공급망", "수요", "공급", "업사이클", "다운사이클",
            "생산", "공장", "설비", "capex", "수출", "수입", "점유율",
            "경쟁사", "고객사", "산업", "업황", "수주", "계약", "납품", "회복", "둔화",
            "규제", "제재", "정책", "관세", "보조금", "기준금리", "통화정책",
            "행정처분", "조사", "공정위", "금융위", "금감원", "특허", "소송",
            "사고", "화재", "리콜", "셧다운", "중단", "결함", "장애",
            "분쟁", "압수수색", "해킹", "유출", "파업", "리스크", "위기",
            "인수", "합병", "매각", "상장폐지", "내부자거래", "불확실성"
    );
    private static final List<String> FOCUS_PRIORITY_KEYWORDS = List.of(
            "실적", "영업이익", "순이익", "매출", "적자", "흑자", "가이던스",
            "컨센서스", "서프라이즈", "수익성", "마진", "원가",
            "수주", "계약", "공급", "납품", "증가", "감소", "급증", "급감",
            "상향", "하향", "인상", "인하", "금리", "환율", "관세", "보조금",
            "규제", "제재", "리콜", "소송", "조사", "생산", "공장", "가동",
            "셧다운", "중단", "장애", "파업", "반도체", "메모리", "배터리",
            "수요", "공급망", "점유율", "전망", "부진", "회복", "둔화", "개선", "악화",
            "업사이클", "다운사이클"
    );
    private static final List<String> BODY_NOISE_KEYWORDS = List.of(
            "무단 전재", "무단전재", "재배포 금지", "재배포금지", "기사제보", "제보는",
            "구독", "관련기사", "저작권자", "all rights reserved", "copyright",
            "디지털투데이", "서울경제tv", "news1", "뉴시스", "연합뉴스",
            "기자", "에디터", "특파원"
    );

    private final NewsRepository newsRepository;
    private final NewsSecurityMapRepository newsSecurityMapRepository;
    private final SentimentResultRepository sentimentResultRepository;
    private final StockRepository stockRepository;
    private final StockAliasRepository stockAliasRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NaverNewsClient naverNewsClient;
    private final NewsArticleExtractorClient articleExtractorClient;
    private final Feature2NewsSentimentClient sentimentClient;
    private final NewsSentimentObservationAsyncService observationAsyncService;

    // v3 확장 지점: 운영 검증 후 모델 폴더/버전을 바꿀 때 application.yml 값만 교체한다.
    @Value("${feature2.news.sentiment.model:kf-deberta-sentiment-v2}")
    private String sentimentModel;

    public Mono<NewsLoadResult> loadNews(Stock stock) {
        return Mono.fromCallable(() -> loadNewsBlocking(stock))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NewsLoadResult> loadNews(Stock stock, boolean forceRefresh) {
        return Mono.fromCallable(() -> loadNewsBlocking(stock, forceRefresh))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NewsCacheInspection> inspectNewsCache(String stockCode) {
        return Mono.fromCallable(() -> inspectNewsCacheBlocking(stockCode))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NewsLoadResult> loadNewsByStockCode(String stockCode) {
        return Mono.fromCallable(() -> loadNewsByStockCodeBlocking(stockCode))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<NewsDetailDto> loadNewsDetail(Long newsId) {
        return Mono.fromCallable(() -> loadNewsDetailBlocking(newsId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private NewsLoadResult loadNewsBlocking(Stock stock) {
        return loadNewsBlocking(stock, false);
    }

    private NewsLoadResult loadNewsBlocking(Stock stock, boolean forceRefresh) {
        List<String> warnings = new ArrayList<>();
        if (stock == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return NewsLoadResult.builder().newsList(List.of()).warnings(warnings).build();
        }
        if (stock.getCompanyName() == null || stock.getCompanyName().isBlank()) {
            warnings.add(NewsWarningCodes.LIST_FETCH_FAILED);
            return NewsLoadResult.builder().newsList(List.of()).warnings(dedupeWarnings(warnings)).build();
        }

        List<News> latestNews = getOrLoadLatestNews(stock.getStockCode(), stock.getCompanyName(), stock, warnings, forceRefresh);
        List<NewsItemDto> newsList = buildNewsList(latestNews, warnings, true, stock.getStockCode(), forceRefresh);
        return NewsLoadResult.builder()
                .newsList(newsList)
                .warnings(dedupeWarnings(warnings))
                .build();
    }

    private NewsLoadResult loadNewsByStockCodeBlocking(String stockCode) {
        List<String> warnings = new ArrayList<>();
        if (stockCode == null || stockCode.isBlank()) {
            return NewsLoadResult.builder().newsList(List.of()).warnings(warnings).build();
        }

        Stock stock = stockRepository.findByStockCodeWithExchangeAndIndustry(stockCode)
                .orElse(null);
        if (stock == null) {
            warnings.add(NewsWarningCodes.LIST_FETCH_FAILED);
            return NewsLoadResult.builder()
                    .newsList(List.of())
                    .warnings(dedupeWarnings(warnings))
                    .build();
        }

        if (stock.getCompanyName() == null || stock.getCompanyName().isBlank()) {
            warnings.add(NewsWarningCodes.LIST_FETCH_FAILED);
            return NewsLoadResult.builder().newsList(List.of()).warnings(dedupeWarnings(warnings)).build();
        }

        List<News> latestNews = getOrLoadLatestNews(stock.getStockCode(), stock.getCompanyName(), stock, warnings);
        List<NewsItemDto> newsList = buildNewsList(latestNews, warnings, false, stock.getStockCode());
        return NewsLoadResult.builder()
                .newsList(newsList)
                .warnings(dedupeWarnings(warnings))
                .build();
    }

    private NewsCacheInspection inspectNewsCacheBlocking(String stockCode) {
        List<String> warnings = new ArrayList<>();
        String listKey = buildNewsListCacheKeyByStock(stockCode);
        CachedNewsListPayload cachedNewsList = readNewsListCache(listKey, warnings);
        if (cachedNewsList == null || cachedNewsList.getItems() == null || cachedNewsList.getItems().isEmpty()) {
            return new NewsCacheInspection(false, null);
        }
        OffsetDateTime latestPublishedAt = null;
        for (CachedNewsListItem item : cachedNewsList.getItems()) {
            if (item == null || item.getNewsId() == null) {
                return new NewsCacheInspection(false, latestPublishedAt);
            }
            if (item.getPublishedAt() != null
                    && (latestPublishedAt == null || item.getPublishedAt().isAfter(latestPublishedAt))) {
                latestPublishedAt = item.getPublishedAt();
            }
            CachedNewsDetail detail = readDetailCache(item.getNewsId(), warnings);
            CachedFocusTextValue focus = readFocusCache(item.getNewsId(), warnings);
            CachedSentimentValue sentiment = readSentimentCache(item.getNewsId(), warnings);
            if (detail == null
                    || focus == null
                    || focus.getFocusText() == null
                    || focus.getFocusText().isBlank()
                    || !isReusableSentiment(sentiment)) {
                return new NewsCacheInspection(false, latestPublishedAt);
            }
        }
        return new NewsCacheInspection(true, latestPublishedAt);
    }

    private NewsDetailDto loadNewsDetailBlocking(Long newsId) {
        if (newsId == null) {
            throw new ResourceNotFoundException(NewsWarningCodes.DETAIL_NOT_FOUND);
        }

        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new ResourceNotFoundException(NewsWarningCodes.DETAIL_NOT_FOUND));
        List<String> warnings = new ArrayList<>();
        BigDecimal sentimentScore = resolveExistingSentimentScore(news, warnings);
        CachedNewsDetail detail = getOrLoadNewsDetail(news, warnings);
        if (detail == null || detail.getBody() == null || detail.getBody().isBlank()) {
            warnings.add(NewsWarningCodes.DETAIL_FETCH_FAILED_PREFIX + newsId);
            return buildFallbackDetail(news, sentimentScore, warnings);
        }
        if (detail.isLowConfidence()) {
            warnings.add(NewsWarningCodes.BODY_LOW_CONFIDENCE_PREFIX + newsId);
        }
        return NewsDetailDto.builder()
                .newsId(news.getNewsId())
                .title(news.getTitle())
                .url(news.getUrl())
                .publisher(news.getSource())
                .publishedAt(news.getPublishedAt())
                .body(detail.getBody())
                .readerSummary(detail.getReaderSummary())
                .sentimentScore(sentimentScore)
                .warnings(dedupeWarnings(warnings))
                .build();
    }

    private List<News> getOrLoadLatestNews(String stockCode, String query, Stock stock, List<String> warnings) {
        return getOrLoadLatestNews(stockCode, query, stock, warnings, false);
    }

    private List<News> getOrLoadLatestNews(String stockCode, String query, Stock stock, List<String> warnings, boolean forceRefresh) {
        String listKey = buildNewsListCacheKeyByStock(stockCode);
        CachedNewsListPayload cachedNewsList = forceRefresh ? null : readNewsListCache(listKey, warnings);
        if (cachedNewsList != null && cachedNewsList.getItems() != null) {
            warnings.addAll(Optional.ofNullable(cachedNewsList.getWarnings()).orElseGet(List::of));
            return toNewsReferences(cachedNewsList.getItems());
        }

        if (forceRefresh || shouldRefreshNewsSource(stock, warnings)) {
            refreshNewsListSource(stock, warnings);
        }
        List<News> latestNews = loadLatestNewsEntities(stock);
        List<NewsItemDto> newsList = buildNewsList(latestNews, warnings, false, stockCode);
        cacheNewsList(listKey, stockCode, newsList, warnings);
        return latestNews;
    }

    private boolean shouldRefreshNewsSource(Stock stock, List<String> warnings) {
        if (stock == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return true;
        }
        CachedNewsRefreshPayload refreshPayload =
                readNewsRefreshCache(buildNewsRefreshCacheKeyByStock(stock.getStockCode()), warnings);
        if (refreshPayload == null || refreshPayload.getLastFetchedAt() == null) {
            return true;
        }
        OffsetDateTime latestKnownPublishedAt = refreshPayload.getLatestPublishedAt();
        if (latestKnownPublishedAt == null) {
            return true;
        }
        OffsetDateTime latestStoredPublishedAt = loadLatestStoredPublishedAt(stock);
        if (latestStoredPublishedAt == null) {
            return true;
        }
        return latestStoredPublishedAt.isBefore(latestKnownPublishedAt);
    }

    private OffsetDateTime loadLatestStoredPublishedAt(Stock stock) {
        return loadLatestNewsEntities(stock).stream()
                .map(News::getPublishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private void refreshNewsListSource(Stock stock, List<String> warnings) {
        try {
            List<NaverNewsClient.NaverNewsArticle> articles = fetchNewsList(stock, warnings);
            upsertNewsMeta(stock, articles, warnings);
            cacheNewsRefresh(stock.getStockCode(), articles, warnings);
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] news list fetch failed. stockCode={}", stock.getStockCode(), ex);
            warnings.add(NewsWarningCodes.LIST_FETCH_FAILED);
        }
    }

    private List<NaverNewsClient.NaverNewsArticle> fetchNewsList(Stock stock, List<String> warnings) {
        return collectRelevantArticles(stock, warnings);
    }

    public List<NewsDatasetExportRow> collectRelevantArticlesForDataset(
            Stock stock,
            int targetCount,
            List<String> warnings
    ) {
        if (stock == null || targetCount <= 0) {
            return List.of();
        }
        List<NaverNewsClient.NaverNewsArticle> articles =
                collectRelevantArticles(stock, warnings, targetCount, NEWS_EXPORT_DISPLAY_LIMIT, Integer.MAX_VALUE);
        String source = buildDatasetSource(stock);
        return articles.stream()
                .map(article -> buildDatasetExportRow(stock, article, source, warnings))
                .filter(row -> (row.focus() != null && !row.focus().isBlank())
                        || (row.detail() != null && !row.detail().isBlank()))
                .toList();
    }

    @Transactional
    protected NewsDatasetExportRow buildDatasetExportRow(
            Stock stock,
            NaverNewsClient.NaverNewsArticle article,
            String source,
            List<String> warnings
    ) {
        if (stock == null || article == null) {
            return new NewsDatasetExportRow(null, null, null, null, source, null);
        }

        News news = upsertNewsMetaArticle(stock, article, warnings);
        if (news == null) {
            return new NewsDatasetExportRow(
                    stock.getStockCode(),
                    normalizeInlineText(article.getTitle()),
                    normalizeInlineText(article.getSummary()),
                    article.getUrl(),
                    source,
                    null
            );
        }

        CachedNewsDetail detail = getOrLoadNewsDetail(news, warnings);
        String focus = buildFocusSentence(
                detail == null ? null : detail.getBody(),
                news.getTitle(),
                news.getSummary()
        );
        String detailText = buildDetailText(detail, news.getSummary(), news.getTitle());

        return new NewsDatasetExportRow(
                stock.getStockCode(),
                normalizeInlineText(focus),
                normalizeInlineText(detailText),
                news.getUrl(),
                source,
                null
        );
    }

    private List<News> loadLatestNewsEntities(Stock stock) {
        return newsSecurityMapRepository.findLatestNewsByStockId(
                stock.getStockId(),
                PageRequest.of(0, NEWS_FETCH_LIMIT)
        );
    }

    @Transactional
    protected void upsertNewsMeta(Stock stock, List<NaverNewsClient.NaverNewsArticle> articles, List<String> warnings) {
        for (NaverNewsClient.NaverNewsArticle article : articles) {
            upsertNewsMetaArticle(stock, article, warnings);
        }
    }

    private News upsertNewsMetaArticle(Stock stock, NaverNewsClient.NaverNewsArticle article, List<String> warnings) {
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
            return saved;
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] news meta upsert failed. url={}", article.getUrl(), ex);
            warnings.add(NewsWarningCodes.META_UPSERT_FAILED);
            return null;
        }
    }

    private List<NaverNewsClient.NaverNewsArticle> collectRelevantArticles(Stock stock, List<String> warnings) {
        return collectRelevantArticles(stock, warnings, NEWS_FETCH_LIMIT, NEWS_FETCH_LIMIT, NEWS_FETCH_MAX_START);
    }

    private List<NaverNewsClient.NaverNewsArticle> collectRelevantArticles(
            Stock stock,
            List<String> warnings,
            int targetCount,
            int display,
            int maxStart
    ) {
        List<NaverNewsClient.NaverNewsArticle> result = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        boolean expandedFetch = false;
        List<String> entityTerms = buildEntityTerms(stock);
        int safeTargetCount = Math.max(1, targetCount);
        int safeDisplay = Math.max(1, display);

        warnings.add(NewsWarningCodes.FILTER_APPLIED);

        for (int start = NEWS_FETCH_START; result.size() < safeTargetCount && start <= maxStart; start += safeDisplay) {
            if (start > NEWS_FETCH_START) {
                expandedFetch = true;
            }

            NaverNewsClient.NaverNewsSearchResult searchResult =
                    naverNewsClient.search(stock.getCompanyName(), safeDisplay, start);
            warnings.addAll(searchResult.getWarnings());

            List<ScoredArticle> filtered = searchResult.getArticles().stream()
                    .map(article -> new ScoredArticle(article, scoreArticleRelevance(article, entityTerms)))
                    .filter(scored -> scored.score() >= 2)
                    .sorted(Comparator.comparingInt(ScoredArticle::score).reversed()
                            .thenComparing(scored -> Optional.ofNullable(scored.article().getPublishedAt()).orElse(OffsetDateTime.MIN),
                                    Comparator.reverseOrder()))
                    .toList();

            for (ScoredArticle scoredArticle : filtered) {
                NaverNewsClient.NaverNewsArticle article = scoredArticle.article();
                if (article.getUrl() != null && seenUrls.add(article.getUrl())) {
                    result.add(article);
                    if (result.size() >= safeTargetCount) {
                        break;
                    }
                }
            }
        }

        if (expandedFetch) {
            warnings.add(NewsWarningCodes.FILTER_EXPANDED_FETCH);
        }
        if (result.size() < safeTargetCount) {
            warnings.add(NewsWarningCodes.FILTER_INSUFFICIENT_RESULT);
        }
        return result;
    }

    private String buildDatasetSource(Stock stock) {
        String stockCode = stock == null ? null : normalizeInlineText(stock.getStockCode());
        String companyName = stock == null ? null : normalizeInlineText(stock.getCompanyName());
        if (stockCode == null && companyName == null) {
            return null;
        }
        if (stockCode == null) {
            return companyName;
        }
        if (companyName == null) {
            return stockCode;
        }
        return stockCode + "|" + companyName;
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
        return scoreArticleRelevance(
                NaverNewsClient.NaverNewsArticle.builder().title(title).summary(summary).build(),
                List.of()
        ) >= 2;
    }

    private int scoreArticleRelevance(NaverNewsClient.NaverNewsArticle article, List<String> entityTerms) {
        String title = article == null ? null : article.getTitle();
        String summary = article == null ? null : article.getSummary();
        int score = 0;
        if (containsAny(summary, EXTERNAL_FACTOR_KEYWORDS)) {
            score += 2;
        }
        if (containsAny(title, EXTERNAL_FACTOR_KEYWORDS)) {
            score += 1;
        }
        if (containsAny(title, entityTerms)) {
            score += 2;
        }
        if (containsAny(summary, entityTerms)) {
            score += 1;
        }
        return score;
    }

    private List<String> buildEntityTerms(Stock stock) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (stock == null) {
            return List.of();
        }
        addEntityTerm(terms, stock.getCompanyName());
        addEntityTerm(terms, CompanyNameNormalizer.extractSearchKeyword(stock.getCompanyName()));
        if (stock.getStockId() != null) {
            for (StockAlias alias : stockAliasRepository.findByStock_StockId(stock.getStockId())) {
                addEntityTerm(terms, alias.getAliasName());
                addEntityTerm(terms, CompanyNameNormalizer.extractSearchKeyword(alias.getAliasName()));
            }
        }
        return List.copyOf(terms);
    }

    private void addEntityTerm(Set<String> terms, String candidate) {
        if (candidate == null) {
            return;
        }
        String trimmed = candidate.trim();
        if (trimmed.length() >= 2) {
            terms.add(trimmed);
        }
    }

    private boolean containsAny(String value, List<String> keywords) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<NewsItemDto> buildNewsList(
            List<News> latestNews,
            List<String> warnings,
            boolean includeSentiment,
            String stockCode
    ) {
        return buildNewsList(latestNews, warnings, includeSentiment, stockCode, false);
    }

    private List<NewsItemDto> buildNewsList(
            List<News> latestNews,
            List<String> warnings,
            boolean includeSentiment,
            String stockCode,
            boolean forceRefresh
    ) {
        if (latestNews == null || latestNews.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> sentimentScores = includeSentiment
                ? resolveSentimentScores(latestNews, warnings, stockCode, forceRefresh)
                : Map.of();
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
                        .sentimentScore(sentimentScores.get(news.getNewsId()))
                        .build())
                .toList();
    }

    private Map<Long, BigDecimal> resolveSentimentScores(List<News> latestNews, List<String> warnings, String stockCode) {
        return resolveSentimentScores(latestNews, warnings, stockCode, false);
    }

    private Map<Long, BigDecimal> resolveSentimentScores(List<News> latestNews, List<String> warnings, String stockCode, boolean forceRefresh) {
        Map<Long, BigDecimal> scores = new LinkedHashMap<>();
        List<NewsSentimentInput> toAnalyze = new ArrayList<>();
        Map<Long, News> pendingNewsById = new LinkedHashMap<>();
        Map<String, Long> pendingNewsIdsByUrl = new LinkedHashMap<>();

        for (News news : latestNews) {
            if (!isSentimentEligible(news)) {
                continue;
            }

            SentimentResolution resolution = getOrAnalyzeSentiment(news, warnings, stockCode, forceRefresh);
            if (resolution.score() != null) {
                scores.put(news.getNewsId(), resolution.score());
                continue;
            }
            if (resolution.pendingInput() != null) {
                toAnalyze.add(resolution.pendingInput());
                pendingNewsById.put(news.getNewsId(), news);
                pendingNewsIdsByUrl.put(news.getUrl(), news.getNewsId());
            }
        }

        analyzePendingSentiments(toAnalyze, pendingNewsById, pendingNewsIdsByUrl, scores, warnings, stockCode);
        return scores;
    }

    private SentimentResolution getOrAnalyzeSentiment(News news, List<String> warnings, String stockCode) {
        return getOrAnalyzeSentiment(news, warnings, stockCode, false);
    }

    private SentimentResolution getOrAnalyzeSentiment(News news, List<String> warnings, String stockCode, boolean forceRefresh) {
        CachedSentimentValue cachedSentiment = forceRefresh ? null : readSentimentCache(news.getNewsId(), warnings);
        if (!forceRefresh && isReusableSentiment(cachedSentiment)) {
            CachedFocusTextValue cachedFocus = readFocusCache(news.getNewsId(), warnings);
            enqueueObservation(news, stockCode, cachedFocus, cachedSentiment.getSentimentScore());
            return SentimentResolution.cached(cachedSentiment.getSentimentScore());
        }

        CachedFocusTextValue focusPayload = getOrLoadFocusText(news, warnings, forceRefresh);
        if (focusPayload == null || focusPayload.getFocusText() == null || focusPayload.getFocusText().isBlank()) {
            warnings.add(NewsWarningCodes.BODY_FETCH_FAILED_PREFIX + hashUrl(news.getUrl()));
            return SentimentResolution.unavailable();
        }

        if (!forceRefresh && cachedSentiment == null) {
            Optional<SentimentResult> existingSentiment = sentimentResultRepository.findById(
                    new SentimentResultId(news.getNewsId(), sentimentModel)
            );
            if (existingSentiment.isPresent()) {
                BigDecimal score = existingSentiment.get().getScore();
                cacheSentiment(news.getNewsId(), score, focusPayload.getFocusTextVersion(), warnings);
                enqueueObservation(news, stockCode, focusPayload, score);
                return SentimentResolution.cached(score);
            }
        }

        return SentimentResolution.pending(new NewsSentimentInput(
                news.getUrl(),
                news.getTitle(),
                news.getSource(),
                news.getPublishedAt(),
                focusPayload.getFocusText()
        ));
    }

    private void analyzePendingSentiments(
            List<NewsSentimentInput> toAnalyze,
            Map<Long, News> pendingNewsById,
            Map<String, Long> pendingNewsIdsByUrl,
            Map<Long, BigDecimal> scores,
            List<String> warnings,
            String stockCode
    ) {
        if (toAnalyze.isEmpty()) {
            return;
        }

        try {
            Feature2NewsSentimentClient.SentimentBatchResponse response =
                    analyzeSentiment(toAnalyze, warnings);
            warnings.addAll(response.warnings());
            for (String invalidScoreUrl : response.invalidScoreUrls()) {
                Long newsId = pendingNewsIdsByUrl.get(invalidScoreUrl);
                News invalidScoreNews = newsId == null ? null : pendingNewsById.get(newsId);
                if (invalidScoreNews != null && invalidScoreNews.getNewsId() != null) {
                    warnings.add(NewsWarningCodes.SENTIMENT_INVALID_SCORE_PREFIX + invalidScoreNews.getNewsId());
                }
            }

            List<Long> resolvedNewsIds = new ArrayList<>();
            for (NewsSentimentResult result : response.results()) {
                Long newsId = pendingNewsIdsByUrl.get(result.url());
                News matchedNews = newsId == null ? null : pendingNewsById.get(newsId);
                if (matchedNews == null || matchedNews.getNewsId() == null) {
                    warnings.add(NewsWarningCodes.SENTIMENT_INVALID_RESPONSE);
                    continue;
                }
                if (result.sentimentScore() == null) {
                    warnings.add(NewsWarningCodes.SENTIMENT_INVALID_SCORE_PREFIX + matchedNews.getNewsId());
                    continue;
                }
                resolvedNewsIds.add(matchedNews.getNewsId());
                scores.put(matchedNews.getNewsId(), result.sentimentScore());
                CachedFocusTextValue focusPayload = getOrLoadFocusText(matchedNews, warnings);
                String focusTextVersion = focusPayload == null ? null : focusPayload.getFocusTextVersion();
                cacheSentiment(matchedNews.getNewsId(), result.sentimentScore(), focusTextVersion, warnings);
                upsertSentimentResult(matchedNews, result.sentimentScore(), warnings);
                enqueueObservation(matchedNews, stockCode, focusPayload, result);
            }

            for (Map.Entry<Long, News> entry : pendingNewsById.entrySet()) {
                News news = entry.getValue();
                if (news == null || news.getNewsId() == null) {
                    continue;
                }
                if (!resolvedNewsIds.contains(entry.getKey())
                        && !response.invalidScoreUrls().contains(news.getUrl())) {
                    warnings.add(NewsWarningCodes.SENTIMENT_FAILED_PREFIX + news.getNewsId());
                }
            }
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] sentiment analyze failed", ex);
            for (NewsSentimentInput input : toAnalyze) {
                Long newsId = pendingNewsIdsByUrl.get(input.url());
                News failedNews = newsId == null ? null : pendingNewsById.get(newsId);
                if (failedNews != null && failedNews.getNewsId() != null) {
                    warnings.add(NewsWarningCodes.SENTIMENT_FAILED_PREFIX + failedNews.getNewsId());
                }
            }
        }
    }

    private BigDecimal resolveExistingSentimentScore(News news, List<String> warnings) {
        CachedSentimentValue cached = readSentimentCache(news.getNewsId(), warnings);
        if (cached != null) {
            return cached.getSentimentScore();
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
            warnings.add(NewsWarningCodes.SENTIMENT_FAILED_PREFIX + news.getNewsId());
        }
    }

    private CachedFocusTextValue getOrLoadFocusText(News news, List<String> warnings) {
        return getOrLoadFocusText(news, warnings, false);
    }

    private CachedFocusTextValue getOrLoadFocusText(News news, List<String> warnings, boolean forceRefresh) {
        CachedFocusTextValue cachedFocus = forceRefresh ? null : readFocusCache(news.getNewsId(), warnings);
        if (!forceRefresh && cachedFocus != null && cachedFocus.getFocusText() != null && !cachedFocus.getFocusText().isBlank()) {
            return cachedFocus;
        }

        CachedNewsDetail detail = getOrLoadNewsDetail(news, warnings, forceRefresh);
        if (detail == null || detail.getBody() == null || detail.getBody().isBlank()) {
            return null;
        }
        if (detail.isLowConfidence()) {
            warnings.add(NewsWarningCodes.BODY_LOW_CONFIDENCE_PREFIX + news.getNewsId());
        }
        return buildAndCacheFocusText(news, detail, warnings);
    }

    private CachedFocusTextValue buildAndCacheFocusText(News news, CachedNewsDetail detail, List<String> warnings) {
        String focusText = buildModelInputText(news, detail);
        if (focusText == null || focusText.isBlank()) {
            return null;
        }
        return cacheFocusText(news.getNewsId(), focusText, warnings);
    }

    private String buildModelInputText(News news, CachedNewsDetail detail) {
        String title = news == null ? null : news.getTitle();
        String focus = buildFocusSentence(
                detail == null ? null : detail.getBody(),
                title,
                news == null ? null : news.getSummary()
        );
        String detailText = buildDetailText(detail, news == null ? null : news.getSummary(), title);

        String normalizedTitle = normalizeInlineText(title);
        String normalizedFocus = normalizeInlineText(focus);
        String normalizedDetail = normalizeInlineText(detailText);
        if (normalizedTitle == null && normalizedFocus == null && normalizedDetail == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[TITLE]").append('\n');
        if (normalizedTitle != null) {
            sb.append(normalizedTitle);
        }
        sb.append("\n\n[FOCUS]\n");
        if (normalizedFocus != null) {
            sb.append(normalizedFocus);
        }
        sb.append("\n\n[DETAIL]\n");
        if (normalizedDetail != null) {
            sb.append(normalizedDetail);
        }
        String text = sb.toString().trim();
        return text.isBlank() ? null : text;
    }

    private String buildFocusSentence(String body, String title, String summary) {
        List<String> bodySentences = splitIntoSentences(body);
        List<String> selected = new ArrayList<>();

        for (String sentence : bodySentences.stream()
                .sorted(Comparator.comparingInt((String sentence) -> scoreFocusSentence(sentence, title)).reversed())
                .toList()) {
            if (sentence == null || sentence.isBlank() || selected.contains(sentence)) {
                continue;
            }
            selected.add(sentence.trim());
            if (selected.size() >= 2 || selected.stream().mapToInt(String::length).sum() >= FOCUS_MAX_LENGTH) {
                break;
            }
        }

        String focus = normalizeInlineText(String.join(" ", selected));
        if (focus != null) {
            return truncateText(focus, FOCUS_MAX_LENGTH);
        }

        String fallback = normalizeInlineText(summary);
        if (fallback != null) {
            return truncateText(fallback, FOCUS_MAX_LENGTH);
        }
        return truncateText(normalizeInlineText(title), FOCUS_MAX_LENGTH);
    }

    private int scoreFocusSentence(String sentence, String title) {
        if (sentence == null || sentence.isBlank()) {
            return Integer.MIN_VALUE;
        }

        String normalized = sentence.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(sentence, FOCUS_PRIORITY_KEYWORDS)) {
            score += 6;
        }
        if (title != null && !title.isBlank()) {
            String[] titleTokens = title.split("\\s+");
            for (String token : titleTokens) {
                if (token != null && token.length() >= 2 && normalized.contains(token.toLowerCase(Locale.ROOT))) {
                    score += 2;
                }
            }
        }
        if (sentence.matches(".*\\d.*")) {
            score += 2;
        }
        if (sentence.contains("%") || sentence.contains("원") || sentence.contains("달러") || sentence.contains("조")
                || sentence.contains("억") || sentence.contains("만")) {
            score += 2;
        }
        if (sentence.length() >= 35 && sentence.length() <= 180) {
            score += 2;
        }
        if (normalized.contains("기자") || normalized.contains("무단전재") || normalized.contains("재배포")
                || normalized.contains("구독") || normalized.contains("관련기사")) {
            score -= 8;
        }
        return score;
    }

    private String buildDetailText(CachedNewsDetail detail, String summary, String title) {
        String detailSummary = detail == null ? null : detail.getReaderSummary();
        String normalizedSummary = normalizeInlineText(detailSummary);
        if (normalizedSummary != null) {
            return truncateText(normalizedSummary, DETAIL_MAX_LENGTH);
        }

        String normalizedNewsSummary = normalizeInlineText(summary);
        if (normalizedNewsSummary != null) {
            return truncateText(normalizedNewsSummary, DETAIL_MAX_LENGTH);
        }
        return truncateText(normalizeInlineText(title), DETAIL_MAX_LENGTH);
    }

    private List<String> splitIntoSentences(String body) {
        String normalized = normalizeBody(body);
        if (normalized == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split("(?<=[.!?]|다\\.)\\s+"))
                .map(this::normalizeInlineText)
                .filter(sentence -> sentence != null && sentence.length() >= 12)
                .toList();
    }

    private String normalizeInlineText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String truncateText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private String normalizeBody(String body) {
        if (body == null) {
            return null;
        }
        String normalized = java.util.Arrays.stream(body.split("\\R"))
                .map(line -> line == null ? null : line.replace('\u00A0', ' '))
                .map(this::normalizeInlineText)
                .filter(line -> line != null && !isNoiseLine(line))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
        if (normalized == null) {
            return null;
        }
        normalized = normalized
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isNoiseLine(String line) {
        if (line == null) {
            return true;
        }
        String normalized = line.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return true;
        }
        if (normalized.length() <= 3) {
            return false;
        }
        if (normalized.matches("^\\[[^\\]]{1,80}]$")) {
            return true;
        }
        if (normalized.matches("^\\[[^\\]]{1,120}]\\s*,?\\s*\\[[^\\]]{1,160}]$")) {
            return true;
        }
        if (normalized.matches("^\\([^)]{1,120}\\)$")) {
            return true;
        }
        if (normalized.matches(".*\\b([가-힣a-z]{2,20})\\s+(기자|에디터|특파원)\\b.*")) {
            return true;
        }
        if (normalized.matches(".*\\b[a-z0-9.-]+\\.(co\\.kr|com|net)\\b.*")) {
            return true;
        }
        return containsAny(normalized, BODY_NOISE_KEYWORDS);
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

    private CachedNewsDetail getOrLoadNewsDetail(News news, List<String> warnings) {
        return getOrLoadNewsDetail(news, warnings, false);
    }

    private CachedNewsDetail getOrLoadNewsDetail(News news, List<String> warnings, boolean forceRefresh) {
        CachedNewsDetail cachedDetail = forceRefresh ? null : readDetailCache(news.getNewsId(), warnings);
        if (!forceRefresh && cachedDetail != null) {
            return cachedDetail;
        }
        try {
            CachedNewsDetail extractedDetail = extractNewsDetail(news);
            if (extractedDetail != null) {
                cacheNewsDetail(extractedDetail, warnings);
            }
            return extractedDetail;
        } catch (Exception ex) {
            log.warn("[NewsSentimentService] detail fetch failed. newsId={}", news.getNewsId(), ex);
            return null;
        }
    }

    private CachedNewsDetail extractNewsDetail(News news) {
        NewsArticleExtractorClient.ArticleExtractionResult extraction = articleExtractorClient.fetchArticleBody(news.getUrl());
        String body = normalizeBody(extraction.body());
        if (body == null || body.isBlank()) {
            return null;
        }

        List<String> paragraphs = extraction.paragraphs() == null || extraction.paragraphs().isEmpty()
                ? splitBodyParagraphs(body)
                : extraction.paragraphs().stream()
                        .map(this::normalizeBody)
                        .filter(paragraph -> paragraph != null && !paragraph.isBlank())
                        .toList();

        return CachedNewsDetail.builder()
                .newsId(news.getNewsId())
                .title(news.getTitle())
                .url(news.getUrl())
                .publisher(news.getSource())
                .publishedAt(news.getPublishedAt())
                .body(body)
                .paragraphs(paragraphs)
                .readerSummary(buildReaderSummary(body))
                .fetchedAt(OffsetDateTime.now())
                .lowConfidence(extraction.lowConfidence())
                .extractionMeta(extraction.extractionMeta())
                .build();
    }

    private CachedNewsListPayload readNewsListCache(String key, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue().get(key).block(REDIS_BLOCK_TIMEOUT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedNewsListPayload.class);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_READ_FAILED);
            return null;
        }
    }

    private void writeNewsListCache(String key, CachedNewsListPayload payload, List<String> warnings) {
        try {
            redisTemplate.opsForValue()
                    .set(key, objectMapper.writeValueAsString(payload), NEWS_LIST_TTL)
                    .block(REDIS_BLOCK_TIMEOUT);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_WRITE_FAILED);
        }
    }

    private void cacheNewsList(String key, String stockCode, List<NewsItemDto> newsList, List<String> warnings) {
        CachedNewsListPayload payload = CachedNewsListPayload.builder()
                .stockCode(normalizeCacheSegment(stockCode))
                .cachedAt(OffsetDateTime.now())
                .warnings(dedupeWarnings(warnings))
                .items(newsList.stream()
                        .map(item -> CachedNewsListItem.builder()
                                .newsId(item.getNewsId())
                                .title(item.getTitle())
                                .url(item.getUrl())
                                .publisher(item.getPublisher())
                                .publishedAt(item.getPublishedAt())
                                .summary(item.getSummary())
                                .build())
                        .toList())
                .build();
        writeNewsListCache(key, payload, warnings);
    }

    private CachedNewsRefreshPayload readNewsRefreshCache(String key, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue().get(key).block(REDIS_BLOCK_TIMEOUT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedNewsRefreshPayload.class);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_READ_FAILED);
            return null;
        }
    }

    private void writeNewsRefreshCache(String key, CachedNewsRefreshPayload payload, List<String> warnings) {
        try {
            redisTemplate.opsForValue()
                    .set(key, objectMapper.writeValueAsString(payload), NEWS_REFRESH_TTL)
                    .block(REDIS_BLOCK_TIMEOUT);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_WRITE_FAILED);
        }
    }

    private void cacheNewsRefresh(String stockCode, List<NaverNewsClient.NaverNewsArticle> articles, List<String> warnings) {
        NaverNewsClient.NaverNewsArticle latest = articles == null || articles.isEmpty()
                ? null
                : articles.stream()
                        .filter(article -> article.getPublishedAt() != null)
                        .max(Comparator.comparing(NaverNewsClient.NaverNewsArticle::getPublishedAt))
                        .orElse(null);
        CachedNewsRefreshPayload payload = CachedNewsRefreshPayload.builder()
                .stockCode(normalizeCacheSegment(stockCode))
                .lastFetchedAt(OffsetDateTime.now())
                .latestPublishedAt(latest == null ? null : latest.getPublishedAt())
                .latestUrl(latest == null ? null : latest.getUrl())
                .build();
        writeNewsRefreshCache(buildNewsRefreshCacheKeyByStock(stockCode), payload, warnings);
    }

    private CachedNewsDetail readDetailCache(Long newsId, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue()
                    .get(buildNewsDetailCacheKey(newsId))
                    .block(REDIS_BLOCK_TIMEOUT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedNewsDetail.class);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_READ_FAILED);
            return null;
        }
    }

    private void writeDetailCache(CachedNewsDetail detail, List<String> warnings) {
        try {
            redisTemplate.opsForValue()
                    .set(buildNewsDetailCacheKey(detail.getNewsId()), objectMapper.writeValueAsString(detail), NEWS_DETAIL_TTL)
                    .block(REDIS_BLOCK_TIMEOUT);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_WRITE_FAILED);
        }
    }

    private void cacheNewsDetail(CachedNewsDetail detail, List<String> warnings) {
        writeDetailCache(detail, warnings);
    }

    private CachedFocusTextValue readFocusCache(Long newsId, List<String> warnings) {
        try {
            String value = redisTemplate.opsForValue().get(buildNewsFocusCacheKey(newsId)).block(REDIS_BLOCK_TIMEOUT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedFocusTextValue.class);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_READ_FAILED);
            return null;
        }
    }

    private CachedFocusTextValue cacheFocusText(Long newsId, String focusText, List<String> warnings) {
        CachedFocusTextValue payload = CachedFocusTextValue.builder()
                .newsId(newsId)
                .focusText(focusText)
                .generatedAt(OffsetDateTime.now())
                .focusTextVersion(buildFocusTextVersion(focusText))
                .build();
        try {
            redisTemplate.opsForValue()
                    .set(buildNewsFocusCacheKey(newsId), objectMapper.writeValueAsString(payload), NEWS_FOCUS_TTL)
                    .block(REDIS_BLOCK_TIMEOUT);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_WRITE_FAILED);
        }
        return payload;
    }

    private CachedSentimentValue readSentimentCache(Long newsId, List<String> warnings) {
        if (newsId == null) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue()
                    .get(buildNewsSentimentCacheKey(newsId))
                    .block(REDIS_BLOCK_TIMEOUT);
            if (value == null || value.isBlank()) {
                return null;
            }
            return objectMapper.readValue(value, CachedSentimentValue.class);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_READ_FAILED);
            return null;
        }
    }

    private void writeSentimentCache(Long newsId, BigDecimal score, String focusTextVersion, List<String> warnings) {
        if (newsId == null || score == null) {
            return;
        }
        try {
            CachedSentimentValue payload = CachedSentimentValue.builder()
                    .newsId(newsId)
                    .sentimentScore(score)
                    .analyzedAt(OffsetDateTime.now())
                    .modelVersion(sentimentModel)
                    .promptVersion(NEWS_SENTIMENT_PROMPT_VERSION)
                    .focusTextVersion(focusTextVersion)
                    .build();
            redisTemplate.opsForValue()
                    .set(buildNewsSentimentCacheKey(newsId), objectMapper.writeValueAsString(payload), NEWS_SENTIMENT_TTL)
                    .block(REDIS_BLOCK_TIMEOUT);
        } catch (Exception ex) {
            warnings.add(NewsWarningCodes.CACHE_WRITE_FAILED);
        }
    }

    private void cacheSentiment(Long newsId, BigDecimal score, String focusTextVersion, List<String> warnings) {
        writeSentimentCache(newsId, score, focusTextVersion, warnings);
    }

    private Feature2NewsSentimentClient.SentimentBatchResponse analyzeSentiment(
            List<NewsSentimentInput> toAnalyze,
            List<String> warnings
    ) {
        // This service is intentionally executed from loadNewsBlocking on boundedElastic.
        return sentimentClient.analyze(toAnalyze, sentimentModel)
                .block(Duration.ofSeconds(12));
    }

    String buildNewsListCacheKeyByStock(String stockCode) {
        return NEWS_LIST_KEY_PREFIX + normalizeCacheSegment(stockCode);
    }

    String buildNewsRefreshCacheKeyByStock(String stockCode) {
        return NEWS_REFRESH_KEY_PREFIX + normalizeCacheSegment(stockCode);
    }

    String buildNewsDetailCacheKey(Long newsId) {
        return NEWS_DETAIL_KEY_PREFIX + newsId;
    }

    String buildNewsFocusCacheKey(Long newsId) {
        return NEWS_FOCUS_KEY_PREFIX + newsId;
    }

    String buildNewsSentimentCacheKey(Long newsId) {
        return NEWS_SENTIMENT_KEY_PREFIX + newsId;
    }

    String normalizeCacheSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{L}\\p{N}-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    boolean isReusableSentiment(CachedSentimentValue cached) {
        if (cached == null) {
            return false;
        }
        return sentimentModel.equals(cached.getModelVersion())
                && NEWS_SENTIMENT_PROMPT_VERSION.equals(cached.getPromptVersion());
    }

    private String hashUrl(String url) {
        return hashValue(url);
    }

    private String buildFocusTextVersion(String focusText) {
        return hashValue(focusText);
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private List<News> toNewsReferences(List<CachedNewsListItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(item -> {
            News news = new News();
            news.setNewsId(item.getNewsId());
            news.setTitle(item.getTitle());
            news.setUrl(item.getUrl());
            news.setSource(item.getPublisher());
            news.setPublishedAt(item.getPublishedAt());
            news.setSummary(item.getSummary());
            return news;
        }).toList();
    }

    private List<String> splitBodyParagraphs(String body) {
        return java.util.Arrays.stream(body.split("\\n\\n+"))
                .map(this::normalizeBody)
                .filter(paragraph -> paragraph != null && !paragraph.isBlank())
                .toList();
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
            warnings.add(NewsWarningCodes.META_UPSERT_FAILED);
            return false;
        }
        if (news.getUrl() == null || news.getUrl().isBlank()) {
            warnings.add(NewsWarningCodes.META_UPSERT_FAILED);
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

    private void enqueueObservation(
            News news,
            String stockCode,
            CachedFocusTextValue focusPayload,
            BigDecimal predictedScore
    ) {
        enqueueObservation(news, stockCode, focusPayload, new NewsSentimentResult(
                news == null ? null : news.getUrl(),
                predictedScore,
                null,
                null,
                null,
                null,
                sentimentModel,
                NEWS_SENTIMENT_INPUT_FORMAT_VERSION
        ));
    }

    private void enqueueObservation(
            News news,
            String stockCode,
            CachedFocusTextValue focusPayload,
            NewsSentimentResult result
    ) {
        if (news == null
                || news.getNewsId() == null
                || stockCode == null
                || stockCode.isBlank()
                || focusPayload == null
                || focusPayload.getFocusText() == null
                || focusPayload.getFocusText().isBlank()
                || focusPayload.getFocusTextVersion() == null
                || focusPayload.getFocusTextVersion().isBlank()
                || result == null
                || result.sentimentScore() == null) {
            return;
        }
        String modelVersion = result.modelVersion() == null || result.modelVersion().isBlank()
                ? sentimentModel
                : result.modelVersion();
        String inputFormatVersion = result.inputFormatVersion() == null || result.inputFormatVersion().isBlank()
                ? NEWS_SENTIMENT_INPUT_FORMAT_VERSION
                : result.inputFormatVersion();
        observationAsyncService.saveObservation(new NewsSentimentObservationCommand(
                news.getNewsId(),
                stockCode,
                news.getTitle(),
                news.getUrl(),
                news.getSource(),
                news.getPublishedAt(),
                focusPayload.getFocusText(),
                result.sentimentScore(),
                result.predictedLabel(),
                result.negativeProb(),
                result.neutralProb(),
                result.positiveProb(),
                modelVersion,
                inputFormatVersion,
                inputFormatVersion,
                focusPayload.getFocusTextVersion()
        ));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class CachedNewsListPayload {
        private String stockCode;
        private OffsetDateTime cachedAt;
        private List<String> warnings;
        private List<CachedNewsListItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class CachedNewsRefreshPayload {
        private String stockCode;
        private OffsetDateTime lastFetchedAt;
        private OffsetDateTime latestPublishedAt;
        private String latestUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    private static class CachedNewsListItem {
        private Long newsId;
        private String title;
        private String url;
        private String publisher;
        private OffsetDateTime publishedAt;
        private String summary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class CachedNewsDetail {
        private Long newsId;
        private String title;
        private String url;
        private String publisher;
        private OffsetDateTime publishedAt;
        private String body;
        private List<String> paragraphs;
        private String readerSummary;
        private OffsetDateTime fetchedAt;
        private boolean lowConfidence;
        private String extractionMeta;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class CachedFocusTextValue {
        private Long newsId;
        private String focusText;
        private OffsetDateTime generatedAt;
        private String focusTextVersion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class CachedSentimentValue {
        private Long newsId;
        private BigDecimal sentimentScore;
        private OffsetDateTime analyzedAt;
        private String modelVersion;
        private String promptVersion;
        private String focusTextVersion;
    }

    private record SentimentResolution(
            BigDecimal score,
            NewsSentimentInput pendingInput
    ) {
        private static SentimentResolution cached(BigDecimal score) {
            return new SentimentResolution(score, null);
        }

        private static SentimentResolution pending(NewsSentimentInput input) {
            return new SentimentResolution(null, input);
        }

        private static SentimentResolution unavailable() {
            return new SentimentResolution(null, null);
        }
    }

    private record ScoredArticle(
            NaverNewsClient.NaverNewsArticle article,
            int score
    ) {
    }

    public record NewsCacheInspection(
            boolean hit,
            OffsetDateTime cacheAsOf
    ) {
    }

    public record NewsDatasetExportRow(
            String stockCode,
            String focus,
            String detail,
            String contentUri,
            String source,
            String label
    ) {
    }
}
