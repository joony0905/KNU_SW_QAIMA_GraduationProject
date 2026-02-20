package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.dto.MarketSnapshotDto;
import com.qaima.repository.MarketSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotService {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final PlatformTransactionManager transactionManager;

    public Mono<MarketSnapshot> upsertFromKis(Stock stock, KisStatResponseDto.Output output, LocalDate asOfDate) {
        return Blocking.call(() -> tx().execute(status -> {
            LocalDate baseDate = (asOfDate != null ? asOfDate : LocalDate.now());

            MarketSnapshot snap = marketSnapshotRepository
                    .findByStockAndAsOfDate(stock, baseDate)
                    .orElseGet(MarketSnapshot::new);

            snap.setStock(stock);
            snap.setAsOfDate(baseDate);
            snap.setSource("KIS");

            BigDecimal marketCap = parseNullableBigDecimal(output.getHts_avls());
            BigDecimal per = parseNullableBigDecimal(output.getPer());
            BigDecimal pbr = parseNullableBigDecimal(output.getPbr());
            BigDecimal sharesOutstanding = parseNullableBigDecimal(output.getLstn_stcn());

            BigDecimal price = parseNullableBigDecimal(output.getStck_prpr());
            BigDecimal eps = parseNullableBigDecimal(output.getEps());
            BigDecimal bps = parseNullableBigDecimal(output.getBps());

            // KIS hts_avls는 "억원" 단위로 제공되는 경우가 많으므로 원 단위로 통일
            if (marketCap != null) {
                marketCap = marketCap.multiply(BigDecimal.valueOf(100_000_000L));
            }

            if (marketCap == null && price != null && sharesOutstanding != null) {
                marketCap = price.multiply(sharesOutstanding);
            }
            if (per == null && price != null && eps != null && eps.signum() != 0) {
                per = price.divide(eps, 4, RoundingMode.HALF_UP);
            }
            if (pbr == null && price != null && bps != null && bps.signum() != 0) {
                pbr = price.divide(bps, 4, RoundingMode.HALF_UP);
            }

            snap.setMarketCap(marketCap);
            snap.setPer(per);
            snap.setPbr(pbr);
            snap.setSharesOutstanding(sharesOutstanding);

            MarketSnapshot saved = marketSnapshotRepository.save(snap);

            log.info("[MarketSnapshotService] upsert 완료: stockCode={}, asOfDate={}",
                    stock.getStockCode(), baseDate);

            return saved;
        }));
    }

    public Mono<MarketSnapshotDto> getLatestDto(Stock stock, LocalDate asOfDate) {
        return Blocking.call(() -> {
            var opt = (asOfDate == null)
                    ? marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock)
                    : marketSnapshotRepository.findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate);

            return opt.map(this::toDto).orElse(null);
        });
    }

    public MarketSnapshotDto toDto(MarketSnapshot s) {
        if (s == null) return null;

        return MarketSnapshotDto.builder()
                .asOfDate(s.getAsOfDate())
                .marketCap(s.getMarketCap())
                .per(bdToDouble(s.getPer()))
                .pbr(bdToDouble(s.getPbr()))
                .sharesOutstanding(s.getSharesOutstanding())
                .source(s.getSource())
                .build();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static BigDecimal parseNullableBigDecimal(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        t = t.replace(",", "");
        try { return new BigDecimal(t); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseNullableDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        t = t.replace(",", "");
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
    }

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
