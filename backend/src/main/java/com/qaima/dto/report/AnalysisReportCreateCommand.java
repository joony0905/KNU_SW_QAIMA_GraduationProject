package com.qaima.dto.report;

import java.time.Instant;

public record AnalysisReportCreateCommand(
        Long userId,
        String featureType,
        String subjectType,
        String title,
        String stockCode,
        String companyName,
        String portfolioSummary,
        String analysisModel,
        String investLevel,
        String riskProfile,
        String analysisWindow,
        String priceBasis,
        Instant generatedAt,
        String dataAsOf,
        Object requestPayload,
        Object resultSnapshot,
        Object warnings
) {
}
