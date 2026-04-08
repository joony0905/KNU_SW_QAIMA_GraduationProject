package com.qaima.common;

public final class NewsWarningCodes {

    public static final String LIST_FETCH_FAILED = "NEWS_LIST_FETCH_FAILED";
    public static final String DETAIL_NOT_FOUND = "NEWS_DETAIL_NOT_FOUND";
    public static final String DETAIL_FETCH_FAILED_PREFIX = "NEWS_DETAIL_FETCH_FAILED:";
    public static final String BODY_LOW_CONFIDENCE_PREFIX = "NEWS_BODY_LOW_CONFIDENCE:";
    public static final String BODY_FETCH_FAILED_PREFIX = "NEWS_BODY_FETCH_FAILED:";
    public static final String META_UPSERT_FAILED = "NEWS_META_UPSERT_FAILED";
    public static final String FILTER_APPLIED = "NEWS_FILTER_APPLIED";
    public static final String FILTER_EXPANDED_FETCH = "NEWS_FILTER_EXPANDED_FETCH";
    public static final String FILTER_INSUFFICIENT_RESULT = "NEWS_FILTER_INSUFFICIENT_RESULT";
    public static final String INVALID_ITEM_SKIPPED = "NEWS_INVALID_ITEM_SKIPPED";
    public static final String PUBDATE_PARSE_FAILED = "NEWS_PUBDATE_PARSE_FAILED";
    public static final String SENTIMENT_INVALID_RESPONSE = "NEWS_SENTIMENT_INVALID_RESPONSE";
    public static final String SENTIMENT_INVALID_SCORE_PREFIX = "NEWS_SENTIMENT_INVALID_SCORE:";
    public static final String SENTIMENT_FAILED_PREFIX = "NEWS_SENTIMENT_FAILED:";
    public static final String CACHE_READ_FAILED = "NEWS_CACHE_READ_FAILED";
    public static final String CACHE_WRITE_FAILED = "NEWS_CACHE_WRITE_FAILED";

    private NewsWarningCodes() {
    }
}
