package com.qaima.service.marketmetric;

import com.qaima.common.Blocking;
import com.qaima.domain.Exchange;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.ShareClass;
import com.qaima.domain.Stock;
import com.qaima.external.KrStockClient;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.service.marketmetric.model.ShareBasisView;
import com.qaima.service.marketmetric.support.ShareBasisSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShareBasisResolver {

    private static final String SHARE_TYPE_COMMON = "보통주";
    private static final String SHARE_TYPE_PREFERRED = "우선주";
    private static final String SHARE_TYPE_TOTAL = "합계";

    private final IssuedSharesRepository issuedSharesRepository;
    private final KrStockClient krStockClient;

    public Mono<ShareBasisView> resolve(Stock stock, LocalDate asOfDate, boolean allowKisFallback) {
        return Blocking.call(() -> Optional.ofNullable(findPrimaryIssuedShares(stock, asOfDate)))
                .flatMap(issuedShares -> {
                    ShareBasisView fromIssuedShares = fromIssuedShares(issuedShares.orElse(null));
                    if (fromIssuedShares.sharesOutstanding() != null || !allowKisFallback) {
                        return Mono.just(fromIssuedShares);
                    }
                    return resolveFromKis(stock).defaultIfEmpty(fromIssuedShares);
                });
    }

    public IssuedShares findPrimaryIssuedShares(Stock stock, LocalDate asOfDate) {
        if (stock == null || asOfDate == null) {
            return null;
        }
        if (stock.getShareClass() == ShareClass.PREFERRED) {
            return findIssuedShares(stock, SHARE_TYPE_PREFERRED, asOfDate);
        }
        IssuedShares issuedShares = findIssuedShares(stock, SHARE_TYPE_COMMON, asOfDate);
        if (issuedShares == null) {
            issuedShares = findIssuedShares(stock, SHARE_TYPE_TOTAL, asOfDate);
        }
        return issuedShares;
    }

    public ShareBasisView fromIssuedShares(IssuedShares issuedShares) {
        if (issuedShares == null) {
            return new ShareBasisView(null, null, null, null, List.of(), "ISSUED_SHARES_MISSING");
        }

        BigDecimal issuedSharesTotal = ShareBasisSupport.toBigDecimal(issuedShares.getIssuedSharesTotal());
        BigDecimal treasuryShares = ShareBasisSupport.toBigDecimal(issuedShares.getTreasuryShares());
        BigDecimal floatingShares = ShareBasisSupport.toBigDecimal(issuedShares.getFloatingShares());
        return new ShareBasisView(
                ShareBasisSupport.sharesOutstanding(issuedSharesTotal, treasuryShares),
                issuedSharesTotal,
                treasuryShares,
                floatingShares,
                List.of(),
                "ISSUED_SHARES"
        );
    }

    private Mono<ShareBasisView> resolveFromKis(Stock stock) {
        if (stock == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
            return Mono.empty();
        }

        String marketDivCode = toKisMarketDivCode(stock.getExchange());
        if ("B".equals(marketDivCode)) {
            return Mono.empty();
        }

        return krStockClient.fetchKisStatRaw(stock.getStockCode(), marketDivCode)
                .map(output -> parseNullableBigDecimal(output.getLstn_stcn()))
                .flatMap(issuedSharesTotal -> {
                    if (issuedSharesTotal == null) {
                        return Mono.empty();
                    }
                    return Mono.just(new ShareBasisView(
                            issuedSharesTotal,
                            issuedSharesTotal,
                            null,
                            null,
                            List.of(),
                            "KIS_SHARES_FALLBACK"
                    ));
                })
                .onErrorResume(ex -> {
                    log.warn("[ShareBasisResolver] KIS shares fallback failed. stockCode={}",
                            stock.getStockCode(), ex);
                    return Mono.empty();
                });
    }

    private IssuedShares findIssuedShares(Stock stock, String shareType, LocalDate asOfDate) {
        return issuedSharesRepository
                .findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                        stock,
                        shareType,
                        asOfDate
                )
                .orElse(null);
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null || exchange.getCode() == null) {
            return "B";
        }
        return switch (exchange.getCode().trim().toUpperCase()) {
            case "KRX", "XKRX", "KOSPI" -> "J";
            case "KOSDAQ", "XKOS" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    private BigDecimal parseNullableBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
