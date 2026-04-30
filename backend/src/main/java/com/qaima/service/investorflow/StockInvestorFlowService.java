package com.qaima.service.investorflow;

import com.qaima.common.Blocking;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.Stock;
import com.qaima.domain.StockInvestorFlow;
import com.qaima.dto.kis.KisInvestorTradeByStockDailyResponseDto;
import com.qaima.external.KisInvestorFlowClient;
import com.qaima.repository.StockInvestorFlowRepository;
import com.qaima.repository.StockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockInvestorFlowService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int BACKFILL_STEP_DAYS = 21;

    private final StockRepository stockRepository;
    private final StockInvestorFlowRepository flowRepository;
    private final KisInvestorFlowClient kisInvestorFlowClient;

    public Mono<List<StockInvestorFlow>> syncLatest(String stockCode, LocalDate asOfDate) {
        LocalDate baseDate = asOfDate == null ? LocalDate.now(SEOUL) : asOfDate;
        return resolveStock(stockCode)
                .flatMap(stock -> kisInvestorFlowClient.fetchInvestorTradeByStockDaily(stock.getStockCode(), baseDate)
                        .flatMap(rows -> Blocking.call(() -> upsertRows(stock, rows, null, baseDate)))
                        .onErrorResume(ex -> isKisTimeLimited(ex)
                                ? findExistingLatest(stock, baseDate)
                                : Mono.error(ex)));
    }

    public Mono<List<StockInvestorFlow>> backfill(String stockCode, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(SEOUL).minusDays(60) : from;
        LocalDate end = to == null ? LocalDate.now(SEOUL) : to;
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return resolveStock(stockCode)
                .flatMapMany(stock -> Flux.fromIterable(backfillBaseDates(start, end))
                        .concatMap(baseDate -> kisInvestorFlowClient.fetchInvestorTradeByStockDaily(stock.getStockCode(), baseDate)
                                .flatMap(rows -> Blocking.call(() -> upsertRows(stock, rows, start, end)))
                                .onErrorResume(ex -> isKisTimeLimited(ex)
                                        ? findExistingRange(stock, start, end)
                                        : Mono.error(ex))))
                .flatMapIterable(rows -> rows)
                .distinct(flow -> flow.getStockCode() + ":" + flow.getTradeDate())
                .sort(Comparator.comparing(StockInvestorFlow::getTradeDate))
                .collectList();
    }

    public Mono<List<StockInvestorFlow>> latestRows(String stockCode, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 365));
        return resolveStock(stockCode)
                .flatMap(stock -> Blocking.call(() -> flowRepository.findByStockOrderByTradeDateDesc(
                        stock,
                        PageRequest.of(0, safeLimit)
                )));
    }

    private Mono<Stock> resolveStock(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }
        String normalized = normalizeStockCode(stockCode);
        return Blocking.call(() -> stockRepository.findByStockCodeWithExchange(normalized).orElse(null))
                .flatMap(stock -> stock == null
                        ? Mono.error(new IllegalArgumentException("stock not found: " + normalized))
                        : Mono.just(stock));
    }

    private Mono<List<StockInvestorFlow>> findExistingLatest(Stock stock, LocalDate baseDate) {
        return Blocking.call(() -> flowRepository
                .findTopByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(stock, baseDate)
                .map(List::of)
                .orElseGet(List::of));
    }

    private Mono<List<StockInvestorFlow>> findExistingRange(Stock stock, LocalDate from, LocalDate to) {
        return Blocking.call(() -> flowRepository.findByStockAndTradeDateBetweenOrderByTradeDateAsc(stock, from, to));
    }

    private boolean isKisTimeLimited(Throwable ex) {
        return ex instanceof ErrorException errorException
                && ErrorCode.KIS_MARKET_CLOSED.equals(errorException.getErrorCode());
    }

    private List<StockInvestorFlow> upsertRows(
            Stock stock,
            List<KisInvestorTradeByStockDailyResponseDto.Row> rows,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<StockInvestorFlow> saved = new ArrayList<>();
        for (KisInvestorTradeByStockDailyResponseDto.Row row : rows) {
            LocalDate tradeDate = parseDate(row.getStck_bsop_date());
            if (tradeDate == null) {
                continue;
            }
            if (fromInclusive != null && tradeDate.isBefore(fromInclusive)) {
                continue;
            }
            if (toInclusive != null && tradeDate.isAfter(toInclusive)) {
                continue;
            }

            StockInvestorFlow entity = flowRepository
                    .findByStockAndTradeDateAndSource(stock, tradeDate, KisInvestorFlowClient.SOURCE)
                    .orElseGet(StockInvestorFlow::new);

            entity.setStock(stock);
            entity.setStockCode(stock.getStockCode());
            entity.setTradeDate(tradeDate);
            entity.setMarketDivCode(KisInvestorFlowClient.DEFAULT_MARKET_DIV_CODE);
            entity.setClosePrice(parseDecimal(row.getStck_clpr()));
            entity.setAccumulatedVolume(parseDecimal(row.getAcml_vol()));
            entity.setAccumulatedTradingValueMillion(parseDecimal(row.getAcml_tr_pbmn()));
            entity.setForeignNetBuyQty(parseDecimal(row.getFrgn_ntby_qty()));
            entity.setForeignNetBuyValueMillion(parseDecimal(row.getFrgn_ntby_tr_pbmn()));
            entity.setIndividualNetBuyQty(parseDecimal(row.getPrsn_ntby_qty()));
            entity.setIndividualNetBuyValueMillion(parseDecimal(row.getPrsn_ntby_tr_pbmn()));
            entity.setInstitutionNetBuyQty(parseDecimal(row.getOrgn_ntby_qty()));
            entity.setInstitutionNetBuyValueMillion(parseDecimal(row.getOrgn_ntby_tr_pbmn()));
            entity.setSecuritiesNetBuyQty(parseDecimal(row.getScrt_ntby_qty()));
            entity.setSecuritiesNetBuyValueMillion(parseDecimal(row.getScrt_ntby_tr_pbmn()));
            entity.setInvestmentTrustNetBuyQty(parseDecimal(row.getIvtr_ntby_qty()));
            entity.setInvestmentTrustNetBuyValueMillion(parseDecimal(row.getIvtr_ntby_tr_pbmn()));
            entity.setPrivateFundNetBuyQty(parseDecimal(row.getPe_fund_ntby_vol()));
            entity.setPrivateFundNetBuyValueMillion(parseDecimal(row.getPe_fund_ntby_tr_pbmn()));
            entity.setBankNetBuyQty(parseDecimal(row.getBank_ntby_qty()));
            entity.setBankNetBuyValueMillion(parseDecimal(row.getBank_ntby_tr_pbmn()));
            entity.setInsuranceNetBuyQty(parseDecimal(row.getInsu_ntby_qty()));
            entity.setInsuranceNetBuyValueMillion(parseDecimal(row.getInsu_ntby_tr_pbmn()));
            entity.setFundNetBuyQty(parseDecimal(row.getFund_ntby_qty()));
            entity.setFundNetBuyValueMillion(parseDecimal(row.getFund_ntby_tr_pbmn()));
            entity.setOtherNetBuyQty(parseDecimal(row.getEtc_ntby_qty()));
            entity.setOtherNetBuyValueMillion(parseDecimal(row.getEtc_ntby_tr_pbmn()));
            entity.setSource(KisInvestorFlowClient.SOURCE);
            entity.setSourceTrId(KisInvestorFlowClient.TR_ID);

            saved.add(flowRepository.save(entity));
        }

        log.info("[StockInvestorFlow] upsert complete. stockCode={}, rows={}", stock.getStockCode(), saved.size());
        return saved;
    }

    private List<LocalDate> backfillBaseDates(LocalDate from, LocalDate to) {
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
        LocalDate cursor = to;
        while (!cursor.isBefore(from)) {
            dates.add(cursor);
            cursor = cursor.minusDays(BACKFILL_STEP_DAYS);
        }
        dates.add(from);
        return new ArrayList<>(dates);
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

    private String normalizeStockCode(String stockCode) {
        String trimmed = stockCode.trim().replace(".XKRX", "").replace(".XKOS", "");
        if (trimmed.matches("\\d+")) {
            return String.format("%06d", Integer.parseInt(trimmed));
        }
        return trimmed;
    }
}
