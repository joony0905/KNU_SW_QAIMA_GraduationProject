package com.qaima.api.feat3;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.feature3.Feature3OverlayCachePreviewRequestDto;
import com.qaima.dto.feature3.Feature3OverlayCachePreviewResponseDto;
import com.qaima.dto.feature3.PortfolioAnalyzeRequestDto;
import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.dto.feature3.Feature3FastApiAnalyzeRequestDto;
import com.qaima.repository.StockRepository;
import com.qaima.service.credit.CreditService;
import com.qaima.service.feature3.Feature3OverlayService;
import com.qaima.service.feature3.Feature3RiskFreeRateService;
import com.qaima.service.stock.StockMappingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/feature3")
public class Feature3AnalyzeController {

    private final AnalysisApiClient analysisApiClient;
    private final Feature3OverlayService feature3OverlayService;
    private final Feature3RiskFreeRateService feature3RiskFreeRateService;
    private final CreditService creditService;
    private final StockMappingService stockMappingService;
    private final StockRepository stockRepository;

    @PostMapping("/analysis")
    public Mono<ApiResponse<PortfolioAnalyzeResponseDto>> analyze(
            Authentication authentication,
            @Valid @RequestBody PortfolioAnalyzeRequestDto req
    ) {
        Long userId = currentUserId(authentication);
        String referenceId = UUID.randomUUID().toString();
        log.info("[Feature3AnalyzeController][request] holdings={}, options={}, riskGamma={}",
                req.holdings() != null ? req.holdings().size() : 0,
                req.options(),
                req.riskProfile() != null ? req.riskProfile().riskAversionGamma() : null);

        return feature3OverlayService.estimateCredit(req)
                .flatMap(cost -> creditService.useFeature3(userId, cost, referenceId)
                        .then(toFastApiRequest(req))
                        .flatMap(fastApiRequest -> {
                            PortfolioAnalyzeRequestDto resolvedReq = withResolvedHoldings(req, fastApiRequest.holdings());
                            return feature3OverlayService.loadOverlaySignals(resolvedReq)
                                    .map(signals -> withOverlaySignals(fastApiRequest, signals))
                                    .flatMap(requestWithSignals -> analysisApiClient.requestPortfolioAnalysis(requestWithSignals)
                                .map(response -> response.toPublicDto())
                                .flatMap(response -> feature3OverlayService.enrich(
                                        resolvedReq,
                                        response
                                ))
                                .map(ApiResponse::success)
                                .onErrorResume(ex -> creditService.refundFeature3(
                                                userId,
                                                cost,
                                                referenceId,
                                                "FEATURE3_ANALYZE_FAILED"
                                        )
                                        .then(Mono.error(ex))));
                        }));
    }

    @PostMapping("/overlay-cache/preview")
    public Mono<ApiResponse<Feature3OverlayCachePreviewResponseDto>> previewOverlayCache(
            Authentication authentication,
            @Valid @RequestBody Feature3OverlayCachePreviewRequestDto req
    ) {
        currentUserId(authentication);
        return feature3OverlayService.preview(req).map(ApiResponse::success);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }

    private Mono<Feature3FastApiAnalyzeRequestDto> toFastApiRequest(PortfolioAnalyzeRequestDto req) {
        PortfolioAnalyzeRequestDto.Options options = req.options();
        // 현재 구현: public DTO(camelCase)와 FastAPI DTO(snake_case 직렬화 대상)를 경계에서 명시적으로 분리한다.
        // 진행 예정: overlay/credit preview도 같은 원칙으로 별도 DTO를 둔다.
        Mono<List<Feature3FastApiAnalyzeRequestDto.Holding>> holdingsMono = Flux.fromIterable(req.holdings())
                .flatMap(this::toFastApiHolding)
                .collectList();
        Mono<Feature3RiskFreeRateService.RiskFreeRate> riskFreeMono = feature3RiskFreeRateService.resolve();
        return Mono.zip(holdingsMono, riskFreeMono)
                .map(tuple -> {
                    List<Feature3FastApiAnalyzeRequestDto.Holding> holdings = tuple.getT1();
                    Feature3RiskFreeRateService.RiskFreeRate riskFree = tuple.getT2();
                    return new Feature3FastApiAnalyzeRequestDto(
                        req.portfolioId(),
                        normalizeInvestLevel(req.investLevel()),
                        holdings,
                        req.cashPositions() != null
                                ? req.cashPositions().stream()
                                        .map(cash -> new Feature3FastApiAnalyzeRequestDto.CashPosition(
                                                cash.currency() != null ? cash.currency() : "KRW",
                                                cash.amount() != null ? cash.amount() : 0.0
                                        ))
                                        .toList()
                                : List.of(),
                        new Feature3FastApiAnalyzeRequestDto.RiskProfile(
                                req.riskProfile().riskToleranceScore(),
                                req.riskProfile().riskAversionGamma(),
                                req.riskProfile().profileType(),
                                req.riskProfile().targetVolatility()
                        ),
                        new Feature3FastApiAnalyzeRequestDto.Options(
                                options != null && options.viewMode() != null ? options.viewMode() : "BASIC",
                                options != null && options.priceBasis() != null ? options.priceBasis() : "ADJUSTED_CLOSE",
                                options != null && options.covarianceModel() != null ? options.covarianceModel() : "LEDOIT_WOLF",
                                options != null && options.returnType() != null ? options.returnType() : "LOG_RETURN",
                                options != null && options.lookbackTradingDays() != null ? options.lookbackTradingDays() : 252,
                                options != null && options.fetchCalendarDays() != null ? options.fetchCalendarDays() : 370,
                                options != null && options.annualizationFactor() != null ? options.annualizationFactor() : 252,
                                options != null && options.cachePolicy() != null ? options.cachePolicy() : "CORE_ONLY",
                                options != null && options.selectedOverlays() != null ? options.selectedOverlays() : List.of(),
                                options != null && Boolean.TRUE.equals(options.includeFrontier()),
                                options != null && Boolean.TRUE.equals(options.includeDiagnostics()),
                                options != null && Boolean.TRUE.equals(options.includeLlmExplain()),
                                options != null ? options.llmVendor() : null,
                                riskFree.rate(),
                                riskFree.source(),
                                riskFree.asOf(),
                                options != null ? options.maxCashWeight() : null
                        ),
                        List.of()
                );
                });
    }

    private Feature3FastApiAnalyzeRequestDto withOverlaySignals(
            Feature3FastApiAnalyzeRequestDto request,
            List<Feature3FastApiAnalyzeRequestDto.OverlaySignal> overlaySignals
    ) {
        return new Feature3FastApiAnalyzeRequestDto(
                request.portfolioId(),
                request.investLevel(),
                request.holdings(),
                request.cashPositions(),
                request.riskProfile(),
                request.options(),
                overlaySignals != null ? overlaySignals : List.of()
        );
    }

    private PortfolioAnalyzeRequestDto withResolvedHoldings(
            PortfolioAnalyzeRequestDto original,
            List<Feature3FastApiAnalyzeRequestDto.Holding> resolvedHoldings
    ) {
        if (resolvedHoldings == null || resolvedHoldings.isEmpty()) {
            return original;
        }
        List<PortfolioAnalyzeRequestDto.Holding> holdings = resolvedHoldings.stream()
                .map(holding -> new PortfolioAnalyzeRequestDto.Holding(
                        holding.stockCode(),
                        holding.companyName(),
                        holding.quantity(),
                        holding.avgPrice(),
                        holding.currentPrice(),
                        holding.currency(),
                        holding.assetType()
                ))
                .toList();
        return new PortfolioAnalyzeRequestDto(
                original.portfolioId(),
                original.investLevel(),
                holdings,
                original.cashPositions(),
                original.riskProfile(),
                original.options()
        );
    }

    private static String normalizeInvestLevel(String investLevel) {
        if (investLevel == null || investLevel.isBlank()) {
            return "초급자";
        }
        return switch (investLevel.trim()) {
            case "초급자", "중급자", "고급자", "전문가" -> investLevel.trim();
            default -> "초급자";
        };
    }

    private Mono<Feature3FastApiAnalyzeRequestDto.Holding> toFastApiHolding(PortfolioAnalyzeRequestDto.Holding holding) {
        String identifier = firstNonBlank(holding.stockCode(), holding.companyName());
        if (isDirectStockCode(identifier)) {
            return toFastApiHolding(holding, identifier, holding.companyName());
        }

        // 현재 구현: 프론트가 companyName만 보내도 Spring의 StockMappingService로 FastAPI 전달 전 stockCode를 확정한다.
        // 매핑 실패 시에는 기존 입력값으로 fallback하고 FastAPI dataQuality warning에서 실패를 확인하게 둔다.
        return stockMappingService.normalizeStockCodeByName(
                        firstNonBlank(holding.companyName(), holding.stockCode()),
                        null,
                        null
                )
                .map(mapping -> toFastApiHolding(
                        holding,
                        mapping.getStockCode(),
                        firstNonBlank(holding.companyName(), mapping.getCompanyName())
                ))
                .flatMap(mono -> mono)
                .onErrorResume(ex -> {
                    log.warn("[Feature3] stock mapping failed. identifier={}, companyName={}, cause={}",
                            identifier, holding.companyName(), ex.getMessage());
                    return toFastApiHolding(holding, identifier, holding.companyName());
                });
    }

    private Mono<Feature3FastApiAnalyzeRequestDto.Holding> toFastApiHolding(
            PortfolioAnalyzeRequestDto.Holding holding,
            String stockCode,
            String companyName
    ) {
        return resolveExchangeCode(stockCode)
                .map(exchangeCode -> new Feature3FastApiAnalyzeRequestDto.Holding(
                stockCode,
                companyName,
                holding.quantity(),
                holding.avgPrice(),
                holding.currentPrice(),
                holding.currency() != null ? holding.currency() : "KRW",
                holding.assetType() != null ? holding.assetType() : "EQUITY",
                exchangeCode != null && !exchangeCode.isBlank() ? exchangeCode : null
        ));
    }

    private Mono<String> resolveExchangeCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just("");
        }
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stockCode.trim())
                        .map(stock -> stock.getExchange() != null ? stock.getExchange().getCode() : "")
                        .orElse(""))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[Feature3] exchange lookup failed. stockCode={}, cause={}", stockCode, ex.getMessage());
                    return Mono.just("");
                });
    }

    private boolean isDirectStockCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.matches("\\d{6}") || trimmed.matches("[A-Za-z]{1,6}([.:][A-Za-z0-9]+)?");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second != null ? second.trim() : "";
    }
}
