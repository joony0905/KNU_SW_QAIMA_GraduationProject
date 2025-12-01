package com.qaima.external;

import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse;
import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@Component
public class GlobalStockClient {


    private final WebClient webClient;

    @Value("${marketstack.access-key}")
    private String accessKey;

    public GlobalStockClient(
            @Qualifier("marketstackWebClient") WebClient webClient
    ) {
        this.webClient = webClient;
    }

    public Mono<StockDto> fetchStock(Stock stock) {
        String symbol = stock.getStockCode();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + symbol)
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class)
                .map(t -> mapToStockDto(stock, t));
    }

    private StockDto mapToStockDto(Stock s, MarketStackTickersResponse.TickerData t) {

        Double price = (t.getPrice() != null ? t.getPrice() : null);
        Double change = (t.getChangeRate() != null ? t.getChangeRate() : null);

        Long industryId = (s.getIndustry() != null)
                ? s.getIndustry().getIndustryId()
                : null;

        return StockDto.builder()
                .stockId(s.getStockId())
                .stockCode(s.getStockCode())
                .isin(s.getIsin())
                .companyName(s.getCompanyName())

                .exchangeId(s.getExchange().getExchangeId())
                .exchangeCode(s.getExchange().getCode())

                .assetType(s.getAssetType())
                .currency(s.getCurrency())
                .industryId(industryId)

                .price(price)
                .changeRate(change)

                .listedAt(String.valueOf(s.getListedAt()))
                .delistedAt(String.valueOf(s.getDelistedAt()))
                .build();
    }


    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers/" + symbol)
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.TickerData.class);
    }

    public Mono<MarketStackTickersResponse> fetchTickers() {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tickers")
                        .queryParam("access_key", accessKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketStackTickersResponse.class);
    }
}

