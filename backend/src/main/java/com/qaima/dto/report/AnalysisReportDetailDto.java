package com.qaima.dto.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AnalysisReportDetailDto(
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
        String dataAsOf,
        JsonNode requestPayload,
        JsonNode resultSnapshot,
        JsonNode warnings
) {
}
