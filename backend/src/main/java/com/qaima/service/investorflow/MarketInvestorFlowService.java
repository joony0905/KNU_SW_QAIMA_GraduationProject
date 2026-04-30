package com.qaima.service.investorflow;

import com.qaima.common.Blocking;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.MarketInvestorFlow;
import com.qaima.dto.kis.KisInvestorDailyByMarketResponseDto;
import com.qaima.external.KisInvestorFlowClient;
import com.qaima.repository.MarketInvestorFlowRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketInvestorFlowService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final MarketInvestorFlowRepository marketInvestorFlowRepository;
    private final KisInvestorFlowClient kisInvestorFlowClient;

    public Mono<List<MarketInvestorFlow>> sync(
            String marketCode,
            String industryCode,
            LocalDate from,
            LocalDate to
    ) {
        String safeMarketCode = normalizeMarketCode(marketCode);
        String safeIndustryCode = normalizeIndustryCode(industryCode);
        LocalDate end = to == null ? LocalDate.now(SEOUL) : to;
        LocalDate start = from == null ? end : from;
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return kisInvestorFlowClient.fetchInvestorDailyByMarket(safeMarketCode, safeIndustryCode, start, end)
                .flatMap(rows -> Blocking.call(() -> upsertRows(safeMarketCode, safeIndustryCode, rows, start, end)))
                .onErrorResume(ex -> isKisTimeLimited(ex)
                        ? findExistingRange(safeMarketCode, safeIndustryCode, start, end)
                        : Mono.error(ex));
    }

    public Mono<List<MarketInvestorFlow>> latestRows(String marketCode, String industryCode, int limit) {
        String safeMarketCode = normalizeMarketCode(marketCode);
        String safeIndustryCode = normalizeIndustryCode(industryCode);
        int safeLimit = Math.max(1, Math.min(limit, 365));
        return Blocking.call(() -> marketInvestorFlowRepository.findByMarketCodeAndIndustryCodeOrderByTradeDateDesc(
                safeMarketCode,
                safeIndustryCode,
                PageRequest.of(0, safeLimit)
        ));
    }

    private List<MarketInvestorFlow> upsertRows(
            String marketCode,
            String industryCode,
            List<KisInvestorDailyByMarketResponseDto.Row> rows,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<MarketInvestorFlow> saved = rows.stream()
                .map(row -> toEntity(marketCode, industryCode, row, fromInclusive, toInclusive))
                .flatMap(java.util.Optional::stream)
                .map(marketInvestorFlowRepository::save)
                .sorted(Comparator.comparing(MarketInvestorFlow::getTradeDate))
                .toList();

        log.info("[MarketInvestorFlow] upsert complete. marketCode={}, industryCode={}, rows={}",
                marketCode, industryCode, saved.size());
        return saved;
    }

    private java.util.Optional<MarketInvestorFlow> toEntity(
            String marketCode,
            String industryCode,
            KisInvestorDailyByMarketResponseDto.Row row,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        LocalDate tradeDate = parseDate(row.getStck_bsop_date());
        if (tradeDate == null) {
            return java.util.Optional.empty();
        }
        if (fromInclusive != null && tradeDate.isBefore(fromInclusive)) {
            return java.util.Optional.empty();
        }
        if (toInclusive != null && tradeDate.isAfter(toInclusive)) {
            return java.util.Optional.empty();
        }

        MarketInvestorFlow entity = marketInvestorFlowRepository
                .findByMarketCodeAndIndustryCodeAndTradeDateAndSource(
                        marketCode,
                        industryCode,
                        tradeDate,
                        KisInvestorFlowClient.SOURCE
                )
                .orElseGet(MarketInvestorFlow::new);

        entity.setMarketCode(marketCode);
        entity.setIndustryCode(industryCode);
        entity.setTradeDate(tradeDate);
        entity.setForeignNetBuyQty(parseDecimal(row.getFrgn_ntby_qty()));
        entity.setForeignNetBuyValueMillion(parseDecimal(row.getFrgn_ntby_tr_pbmn()));
        entity.setIndividualNetBuyQty(parseDecimal(row.getPrsn_ntby_qty()));
        entity.setIndividualNetBuyValueMillion(parseDecimal(row.getPrsn_ntby_tr_pbmn()));
        entity.setInstitutionNetBuyQty(parseDecimal(row.getOrgn_ntby_qty()));
        entity.setInstitutionNetBuyValueMillion(parseDecimal(row.getOrgn_ntby_tr_pbmn()));
        entity.setSource(KisInvestorFlowClient.SOURCE);
        entity.setSourceTrId(KisInvestorFlowClient.MARKET_TR_ID);
        return java.util.Optional.of(entity);
    }

    private Mono<List<MarketInvestorFlow>> findExistingRange(
            String marketCode,
            String industryCode,
            LocalDate from,
            LocalDate to
    ) {
        return Blocking.call(() -> marketInvestorFlowRepository.findByMarketCodeAndIndustryCodeAndTradeDateBetweenOrderByTradeDateAsc(
                marketCode,
                industryCode,
                from,
                to
        ));
    }

    private boolean isKisTimeLimited(Throwable ex) {
        return ex instanceof ErrorException errorException
                && ErrorCode.KIS_MARKET_CLOSED.equals(errorException.getErrorCode());
    }

    private String normalizeMarketCode(String marketCode) {
        if (marketCode == null || marketCode.isBlank()) {
            return "KSP";
        }
        String normalized = marketCode.trim().toUpperCase();
        if ("KOSPI".equals(normalized)) {
            return "KSP";
        }
        if ("KOSDAQ".equals(normalized)) {
            return "KSQ";
        }
        return normalized;
    }

    private String normalizeIndustryCode(String industryCode) {
        return industryCode == null || industryCode.isBlank()
                ? KisInvestorFlowClient.DEFAULT_MARKET_INDUSTRY_CODE
                : industryCode.trim();
    }

    private LocalDate parseDate(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return LocalDate.parse(raw.trim(), BASIC_DATE);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }
}
