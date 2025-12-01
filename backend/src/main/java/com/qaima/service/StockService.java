package com.qaima.service;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import com.qaima.external.StockApiClient;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockApiClient stockApiClient;

    public Mono<StockDto> getStockWithRealtime(Long stockId) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목 ID: " + stockId));

        Long dtoStockId = null;
        try {
            dtoStockId = stock.getStockId();
        } catch (NoSuchMethodError | RuntimeException ignored) {
        }

        Long dtoExchangeId = null;
        try {
            if (stock.getExchange() != null) {
                dtoExchangeId = stock.getExchange().getExchangeId();
            }
        } catch (NoSuchMethodError | RuntimeException ignored) {
        }

        Long dtoIndustryId = null;
        try {
            if (stock.getIndustry() != null) {
                dtoIndustryId = stock.getIndustry().getIndustryId();
            }
        } catch (NoSuchMethodError | RuntimeException ignored) {
        }

        StockDto base = StockDto.builder()
                .stockId(dtoStockId)
                .stockCode(stock.getStockCode())
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName())
                .exchangeId(dtoExchangeId)
                .exchangeCode(
                        stock.getExchange() != null ? stock.getExchange().getCode() : null
                )
                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(dtoIndustryId)
                .listedAt(stock.getListedAt() != null ? stock.getListedAt().toString() : null)
                .delistedAt(stock.getDelistedAt() != null ? stock.getDelistedAt().toString() : null)
                .build();

        Mono<MarketStackTickersResponse.TickerData> tickerMono =
                stockApiClient.fetchTickerMeta(stock.getStockCode());

        return tickerMono
                .map(ticker -> {
                    if (ticker.getPrice() != null) {
                        base.setPrice(ticker.getPrice());
                    }
                    if (ticker.getChangeRate() != null) {
                        base.setChangeRate(ticker.getChangeRate());
                    }
                    return base;
                })
                .onErrorResume(e -> {
                    log.error("[StockService] 외부 시세 조회 실패: {}", e.getMessage(), e);
                    return Mono.just(base);
                })
                .defaultIfEmpty(base);
    }
}
