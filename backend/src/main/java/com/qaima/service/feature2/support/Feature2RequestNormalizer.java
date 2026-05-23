package com.qaima.service.feature2.support;

import com.qaima.domain.Freq;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.service.feature2.model.Feature2Command;
import org.springframework.stereotype.Component;

@Component
public class Feature2RequestNormalizer {

    private static final Freq DEFAULT_FREQ = Freq.ONE_D;
    private static final int DEFAULT_WINDOW = 90;
    private static final int DEFAULT_PEER_COUNT = 30;
    private static final int DEFAULT_MAX_LAG = 5;
    private static final int DEFAULT_DISPLAY_LIMIT = 30;

    private static final int MIN_WINDOW = 30;
    private static final int MAX_WINDOW = 365;

    private static final int MIN_PEER_COUNT = 3;
    private static final int MAX_PEER_COUNT = 30;

    private static final int MIN_MAX_LAG = 1;
    private static final int MAX_MAX_LAG = 20;

    private static final int MIN_DISPLAY_LIMIT = 1;
    private static final int MAX_DISPLAY_LIMIT = 100;

    public Feature2Command normalize(Feature2AnalyzeRequestDto req) {
        return new Feature2Command(
                normalizeStockCode(req == null ? null : req.getStockCode()),
                normalizeFreq(req == null ? null : req.getFreq()),
                normalizeWindow(req == null ? null : req.getWindow()),
                req == null ? null : req.getFrom(),
                req == null ? null : req.getTo(),
                normalizePeerCount(req == null ? null : req.getPeerCount()),
                normalizeMaxLag(req == null ? null : req.getMaxLag()),
                normalizeDisplayLimit(req == null ? null : req.getDisplayLimit()),
                normalizeLlmVendor(req == null ? null : req.getLlmVendor()),
                normalizeInvestLevel(req == null ? null : req.getInvestLevel())
        );
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null) {
            return null;
        }
        String trimmed = stockCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Freq normalizeFreq(Freq freq) {
        return freq != null ? freq : DEFAULT_FREQ;
    }

    private int normalizeWindow(Integer window) {
        if (window == null) {
            return DEFAULT_WINDOW;
        }
        return Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, window));
    }

    private int normalizePeerCount(Integer peerCount) {
        if (peerCount == null) {
            return DEFAULT_PEER_COUNT;
        }
        return Math.max(MIN_PEER_COUNT, Math.min(MAX_PEER_COUNT, peerCount));
    }

    private int normalizeMaxLag(Integer maxLag) {
        if (maxLag == null) {
            return DEFAULT_MAX_LAG;
        }
        return Math.max(MIN_MAX_LAG, Math.min(MAX_MAX_LAG, maxLag));
    }

    private int normalizeDisplayLimit(Integer displayLimit) {
        if (displayLimit == null) {
            return DEFAULT_DISPLAY_LIMIT;
        }
        return Math.max(MIN_DISPLAY_LIMIT, Math.min(MAX_DISPLAY_LIMIT, displayLimit));
    }

    private String normalizeLlmVendor(String llmVendor) {
        if (llmVendor == null) {
            return null;
        }
        String trimmed = llmVendor.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeInvestLevel(String investLevel) {
        if (investLevel == null || investLevel.isBlank()) {
            return "초급자";
        }
        return switch (investLevel.trim()) {
            case "초급자", "중급자", "고급자", "전문가" -> investLevel.trim();
            default -> "초급자";
        };
    }
}
