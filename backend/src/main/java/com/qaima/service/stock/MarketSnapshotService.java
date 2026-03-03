package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.dto.kis.KisStatResponseDto;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotService {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final PlatformTransactionManager transactionManager;

    public Mono<MarketSnapshot> upsertFromKis(Stock stock, KisStatResponseDto.Output output, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.error(new IllegalArgumentException("stock is required"));
        }
        if (output == null) {
            return Mono.error(new IllegalArgumentException("kis output is required"));
        }

        return Blocking.call(() -> tx().execute(status -> {
            LocalDate baseDate = (asOfDate != null ? asOfDate : LocalDate.now());

            MarketSnapshot snapshot = marketSnapshotRepository
                    .findByStockAndAsOfDate(stock, baseDate)
                    .orElseGet(MarketSnapshot::new);

            snapshot.setStock(stock);
            snapshot.setAsOfDate(baseDate);
            snapshot.setSource("KIS");

            BigDecimal marketCap = parseNullableBigDecimal(output.getHts_avls());
            BigDecimal per = parseNullableBigDecimal(output.getPer());
            BigDecimal pbr = parseNullableBigDecimal(output.getPbr());
            BigDecimal sharesOutstanding = parseNullableBigDecimal(output.getLstn_stcn());

            BigDecimal price = parseNullableBigDecimal(output.getStck_prpr());
            BigDecimal eps = parseNullableBigDecimal(output.getEps());
            BigDecimal bps = parseNullableBigDecimal(output.getBps());

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

            snapshot.setMarketCap(marketCap);
            snapshot.setPer(per);
            snapshot.setPbr(pbr);
            snapshot.setSharesOutstanding(sharesOutstanding);

            MarketSnapshot saved = marketSnapshotRepository.save(snapshot);
            log.info("[MarketSnapshotService] upsert complete: stockCode={}, asOfDate={}",
                    stock.getStockCode(), baseDate);
            return saved;
        }));
    }

    public Mono<MarketSnapshotDto> getLatestDto(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.justOrEmpty((MarketSnapshotDto) null);
        }

        return Blocking.call(() -> {
            var snapshot = (asOfDate == null)
                    ? marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock)
                    : marketSnapshotRepository.findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate);
            return snapshot.map(this::toDto).orElse(null);
        });
    }

    public MarketSnapshotDto toDto(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot.getAsOfDate())
                .marketCap(snapshot.getMarketCap())
                .per(toDouble(snapshot.getPer()))
                .pbr(toDouble(snapshot.getPbr()))
                .sharesOutstanding(snapshot.getSharesOutstanding())
                .source(snapshot.getSource())
                .build();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static BigDecimal parseNullableBigDecimal(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().replace(",", "");
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
