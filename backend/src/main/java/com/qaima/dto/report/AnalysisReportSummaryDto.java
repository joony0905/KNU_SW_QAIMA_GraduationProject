package com.qaima.dto.report;

import java.time.Instant;

public record AnalysisReportSummaryDto(
        Long reportId,
        String featureType,
        String subjectType,
        String title,
        String userName,
        String stockCode,
        String companyName,
        String portfolioSummary,
        String analysisModel,
        String investLevel,
        String riskProfile,
        String analysisWindow,
        String priceBasis,
        Instant generatedAt,
        String dataAsOf
) {
}
