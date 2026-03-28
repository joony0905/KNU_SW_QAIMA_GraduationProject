package com.qaima.common;

public enum Feat2WarningCode {

    // ----- Stock / Industry -----
    STOCK_NOT_FOUND,
    INDUSTRY_MISSING,

    // ----- Industry Index (FR-17) -----
    INDUSTRY_INDEX_MISSING,
    INDUSTRY_INDEX_OHLCV_EMPTY,
    INDUSTRY_INDEX_SAVE_FAILED,
    INDUSTRY_INDEX_FETCH_FAILED,

    // ----- Peer / Cluster (FR-18) -----
    PEER_CLUSTER_CACHE_MISS,
    PEER_CLUSTER_EMPTY,
    PEER_CLUSTER_MISSING,
    PEER_CLUSTER_DB_EMPTY,
    PEER_CLUSTER_CACHE_READ_FAILED,
    PEER_CLUSTER_CACHE_WRITE_FAILED,
    PEER_CLUSTER_DB_READ_FAILED,
    PEER_CLUSTER_JSON_PARSE_FAILED,
    PEER_CLUSTER_INTERNAL_ERROR,
    PEER_CLUSTER_LOAD_FAILED,
    PEER_CLUSTER_INDUSTRY_ID_MISSING,
    PEER_CLUSTER_ANCHOR_MISSING,

    // ----- News / Sentiment -----
    NEWS_NOT_FOUND,
    SENTIMENT_NOT_FOUND,

    // ----- Short Selling -----
    SHORT_SELLING_MISSING,
    SHORT_SELLING_LOAD_FAILED,

    // ----- Base Rate -----
    BASE_RATE_MISSING,
    BASE_RATE_LOAD_FAILED,

    // ----- External / Fallback -----
    EXTERNAL_API_FALLBACK_USED,

    // ----- LLM -----
    LLM_EXPLAIN_FAILED,

    // ----- Feat2 (최상위) -----
    FEAT2_INTERNAL_ERROR
}
