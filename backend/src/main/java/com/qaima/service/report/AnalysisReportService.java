package com.qaima.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.Blocking;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.AnalysisReport;
import com.qaima.domain.User;
import com.qaima.dto.featone.FeatOneAnalyzeRequestDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.feature3.PortfolioAnalyzeRequestDto;
import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import com.qaima.dto.report.AnalysisReportCreateCommand;
import com.qaima.dto.report.AnalysisReportDetailDto;
import com.qaima.dto.report.AnalysisReportSummaryDto;
import com.qaima.repository.AnalysisReportRepository;
import com.qaima.repository.StockRepository;
import com.qaima.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AnalysisReportService {
    private static final java.time.Duration REPORT_RETENTION = java.time.Duration.ofDays(7);

    private final AnalysisReportRepository reportRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    public Mono<Long> createFeature1(Long userId, FeatOneAnalyzeRequestDto request, FeatOneAnalysisResponseDto response) {
        String stockCode = response != null && response.getMetrics() != null
                ? response.getMetrics().getStockCode()
                : request != null ? request.getStockCode() : null;
        String window = buildRange(
                request != null ? request.getFrom() : null,
                request != null ? request.getTo() : null
        );
        return create(new AnalysisReportCreateCommand(
                userId,
                "FEATURE1",
                "STOCK",
                "기능1 종목 분석 리포트",
                resolveCompanyName(stockCode, stockCode),
                stockCode,
                null,
                request != null ? request.getLlmVendor() : null,
                request != null ? request.getInvestLevel() : null,
                null,
                window,
                null,
                Instant.now(),
                response != null && response.getMetrics() != null ? response.getMetrics().getAsOf() : null,
                request,
                response,
                response != null ? response.getWarnings() : null
        ));
    }

    public Mono<Long> createFeature2(Long userId, Feature2AnalyzeRequestDto request, Feature2AnalyzeResponseDto response) {
        Feature2MetricsDto metrics = response != null ? response.getMetrics() : null;
        String stockCode = metrics != null && metrics.getStock() != null
                ? metrics.getStock().getStockCode()
                : request != null ? request.getStockCode() : null;
        String companyName = metrics != null && metrics.getStock() != null
                ? metrics.getStock().getCompanyName()
                : stockCode;
        companyName = resolveCompanyName(stockCode, companyName);
        return create(new AnalysisReportCreateCommand(
                userId,
                "FEATURE2",
                "STOCK",
                "기능2 외부요인 분석 리포트",
                stockCode,
                companyName,
                null,
                request != null ? request.getLlmVendor() : null,
                request != null ? request.getInvestLevel() : null,
                null,
                request != null && request.getWindow() != null ? "최근 " + request.getWindow() + "거래일" : null,
                null,
                Instant.now(),
                feature2DataAsOf(metrics),
                request,
                response,
                response != null ? response.getWarnings() : null
        ));
    }

    public Mono<Long> createFeature3(Long userId, PortfolioAnalyzeRequestDto request, PortfolioAnalyzeResponseDto response) {
        PortfolioAnalyzeRequestDto.Options options = request != null ? request.options() : null;
        PortfolioAnalyzeRequestDto.RiskProfile riskProfile = request != null ? request.riskProfile() : null;
        return create(new AnalysisReportCreateCommand(
                userId,
                "FEATURE3",
                "PORTFOLIO",
                "기능3 포트폴리오 분석 리포트",
                null,
                null,
                portfolioSummary(request),
                options != null ? options.llmVendor() : null,
                request != null ? request.investLevel() : null,
                riskProfileLabel(riskProfile, response),
                options != null && options.lookbackTradingDays() != null ? options.lookbackTradingDays() + "거래일" : null,
                options != null ? options.priceBasis() : null,
                Instant.now(),
                feature3DataAsOf(response),
                request,
                response,
                response != null ? response.warnings() : null
        ));
    }

    public Mono<Long> create(AnalysisReportCreateCommand command) {
        return Blocking.call(() -> {
            User user = userRepository.findById(command.userId())
                    .orElseThrow(() -> new ErrorException(ErrorCode.UNAUTHORIZED, "User not found."));

            AnalysisReport report = new AnalysisReport();
            report.setUser(user);
            report.setFeatureType(required(command.featureType(), "FEATURE"));
            report.setSubjectType(required(command.subjectType(), "STOCK"));
            report.setTitle(required(command.title(), "분석 리포트"));
            report.setUserName(snapshotUserName(user));
            report.setStockCode(blankToNull(command.stockCode()));
            report.setCompanyName(blankToNull(command.companyName()));
            report.setPortfolioSummary(blankToNull(command.portfolioSummary()));
            report.setAnalysisModel(blankToNull(command.analysisModel()));
            report.setInvestLevel(blankToNull(command.investLevel()));
            report.setRiskProfile(blankToNull(command.riskProfile()));
            report.setAnalysisWindow(blankToNull(command.analysisWindow()));
            report.setPriceBasis(blankToNull(command.priceBasis()));
            report.setGeneratedAt(command.generatedAt() != null ? command.generatedAt() : Instant.now());
            report.setDataAsOf(blankToNull(command.dataAsOf()));
            report.setRequestPayloadJson(toJson(command.requestPayload()));
            report.setResultSnapshotJson(toJson(command.resultSnapshot()));
            report.setWarningsJson(command.warnings() == null ? null : toJson(command.warnings()));
            return reportRepository.saveAndFlush(report).getReportId();
        });
    }

    public Mono<java.util.List<AnalysisReportSummaryDto>> listMine(Long userId, String featureType, int page, int size) {
        return Blocking.call(() -> {
            Instant cutoff = retentionCutoff();
            reportRepository.deleteByUser_UserIdAndGeneratedAtBefore(userId, cutoff);
            int safePage = Math.max(0, page);
            int safeSize = Math.max(1, Math.min(size, 50));
            PageRequest pageable = PageRequest.of(safePage, safeSize);
            String normalizedFeature = blankToNull(featureType);
            return (normalizedFeature == null
                    ? reportRepository.findByUser_UserIdAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(userId, cutoff, pageable)
                    : reportRepository.findByUser_UserIdAndFeatureTypeAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(userId, normalizedFeature, cutoff, pageable))
                    .stream()
                    .map(this::toSummary)
                    .toList();
        });
    }

    public Mono<AnalysisReportDetailDto> getMine(Long userId, Long reportId) {
        return Blocking.call(() -> {
            Instant cutoff = retentionCutoff();
            reportRepository.deleteByUser_UserIdAndGeneratedAtBefore(userId, cutoff);
            return reportRepository.findByReportIdAndUser_UserIdAndGeneratedAtGreaterThanEqual(reportId, userId, cutoff)
                .map(this::toDetail)
                .orElseThrow(() -> new ErrorException(ErrorCode.RESOURCE_NOT_FOUND, "Report not found."));
        });
    }

    public Mono<Long> deleteExpiredReports() {
        return Blocking.call(() -> reportRepository.deleteByGeneratedAtBefore(retentionCutoff()));
    }

    private AnalysisReportSummaryDto toSummary(AnalysisReport report) {
        return new AnalysisReportSummaryDto(
                report.getReportId(),
                report.getFeatureType(),
                report.getSubjectType(),
                report.getTitle(),
                report.getUserName(),
                report.getStockCode(),
                report.getCompanyName(),
                report.getPortfolioSummary(),
                report.getAnalysisModel(),
                report.getInvestLevel(),
                report.getRiskProfile(),
                report.getAnalysisWindow(),
                report.getPriceBasis(),
                report.getGeneratedAt(),
                report.getDataAsOf()
        );
    }

    private AnalysisReportDetailDto toDetail(AnalysisReport report) {
        return new AnalysisReportDetailDto(
                report.getReportId(),
                report.getFeatureType(),
                report.getSubjectType(),
                report.getTitle(),
                report.getUserName(),
                report.getStockCode(),
                report.getCompanyName(),
                report.getPortfolioSummary(),
                report.getAnalysisModel(),
                report.getInvestLevel(),
                report.getRiskProfile(),
                report.getAnalysisWindow(),
                report.getPriceBasis(),
                report.getGeneratedAt(),
                report.getDataAsOf(),
                readJson(report.getRequestPayloadJson()),
                readJson(report.getResultSnapshotJson()),
                readJson(report.getWarningsJson())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Report JSON serialization failed", e);
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private Instant retentionCutoff() {
        return Instant.now().minus(REPORT_RETENTION);
    }

    private String feature2DataAsOf(Feature2MetricsDto metrics) {
        if (metrics == null) return null;
        if (metrics.getNewsSentimentSummary() != null && metrics.getNewsSentimentSummary().getSummaryDate() != null) {
            return metrics.getNewsSentimentSummary().getSummaryDate().toString();
        }
        if (metrics.getShortSelling() != null && metrics.getShortSelling().getReportDate() != null) {
            return metrics.getShortSelling().getReportDate().toString();
        }
        if (metrics.getBaseRate() != null && metrics.getBaseRate().getDate() != null) {
            return metrics.getBaseRate().getDate().toString();
        }
        return null;
    }

    private String feature3DataAsOf(PortfolioAnalyzeResponseDto response) {
        if (response == null || response.freshness() == null) return null;
        if (response.freshness().newestDataAt() != null) return response.freshness().newestDataAt();
        return response.freshness().priceSeriesAsOf();
    }

    private String portfolioSummary(PortfolioAnalyzeRequestDto request) {
        if (request == null || request.holdings() == null || request.holdings().isEmpty()) {
            return "포트폴리오";
        }
        List<PortfolioAnalyzeRequestDto.Holding> holdings = request.holdings();
        String firstName = blankToNull(holdings.get(0).companyName());
        if (firstName == null) firstName = holdings.get(0).stockCode();
        int extraCount = Math.max(0, holdings.size() - 1);
        return extraCount == 0 ? firstName : firstName + " 외 " + extraCount + "개 종목";
    }

    private String resolveCompanyName(String stockCode, String fallback) {
        String normalized = blankToNull(stockCode);
        if (normalized == null) return fallback;
        return stockRepository.findByStockCodeWithExchange(normalized)
                .map(stock -> stock.getCompanyName() != null ? stock.getCompanyName() : fallback)
                .orElse(fallback);
    }

    private String riskProfileLabel(PortfolioAnalyzeRequestDto.RiskProfile requestProfile, PortfolioAnalyzeResponseDto response) {
        if (response != null && response.policy() != null && response.policy().riskProfile() != null
                && response.policy().riskProfile().profileType() != null) {
            return response.policy().riskProfile().profileType();
        }
        return requestProfile != null ? requestProfile.profileType() : null;
    }

    private String buildRange(String from, String to) {
        if (blankToNull(from) == null && blankToNull(to) == null) return null;
        return (blankToNull(from) != null ? from : "-") + " ~ " + (blankToNull(to) != null ? to : "-");
    }

    private String snapshotUserName(User user) {
        String name = blankToNull(user.getName());
        return name != null ? name : "사용자";
    }

    private String required(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
