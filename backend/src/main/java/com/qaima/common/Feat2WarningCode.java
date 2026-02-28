package com.qaima.common;

public enum Feat2WarningCode {

    // ----- Stock / Industry -----
    STOCK_NOT_FOUND,
    INDUSTRY_MISSING,

    // ----- Industry Index (FR-17) -----
    INDUSTRY_INDEX_MISSING,
    INDUSTRY_INDEX_OHLCV_EMPTY,

    // ----- Peer / Cluster (FR-18) -----
    PEER_CLUSTER_CACHE_MISS,
    PEER_CLUSTER_EMPTY,
    PEER_CLUSTER_MISSING,
    PEER_CLUSTER_DB_EMPTY,
    PEER_CLUSTER_CACHE_READ_FAILED,
    PEER_CLUSTER_CACHE_WRITE_FAILED,
    PEER_CLUSTER_DB_READ_FAILED,
    PEER_CLUSTER_JSON_PARSE_FAILED,

    // ----- News / Sentiment -----
    NEWS_NOT_FOUND,
    SENTIMENT_NOT_FOUND,

    // ----- External / Fallback -----
    EXTERNAL_API_FALLBACK_USED,

    // ----- LLM -----
    LLM_EXPLAIN_FAILED
}