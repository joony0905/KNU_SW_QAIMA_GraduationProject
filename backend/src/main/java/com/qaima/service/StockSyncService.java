package com.qaima.service;

import com.qaima.domain.Exchange;
import com.qaima.domain.Industry;
import com.qaima.domain.Stock;
import com.qaima.dto.MarketStackTickersResponse.TickerData;
import com.qaima.repository.ExchangeRepository;
import com.qaima.repository.IndustryRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.List;
import com.qaima.common.Blocking;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {

    private final StockRepository stockRepository;
    private final ExchangeRepository exchangeRepository;
    private final IndustryRepository industryRepository;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @Transactional
    public Mono<Void> syncMarketStackTickers(List<TickerData> tickersFromApi) {
        return Mono.fromCallable(() -> {
                    log.info("Marketstack Ticker ?숆린???쒖옉. (珥?{}嫄?", tickersFromApi.size());

                    for (TickerData dto : tickersFromApi) {
                        if (dto.getStock_exchange() == null) {
                            log.warn("Exchange ?뺣낫媛 ?녿뒗 Ticker?낅땲?? {}", dto.getSymbol());
                            continue;
                        }

                        String exchangeCodeValue = dto.getStock_exchange().getAcronym();

                        if (exchangeCodeValue == null) {
                            exchangeCodeValue = dto.getStock_exchange().getMic();
                        }

                        final String finalExchangeCode = exchangeCodeValue;

                        Exchange exchange = exchangeRepository.findByCode(finalExchangeCode)
                                .orElseGet(() -> {
                                    log.info("?덈줈??Exchange ?앹꽦: {}", finalExchangeCode);
                                    Exchange newEx = new Exchange();
                                    newEx.setCode(finalExchangeCode);
                                    newEx.setName(dto.getStock_exchange().getName());
                                    newEx.setCountry(dto.getStock_exchange().getCountry());
                                    return exchangeRepository.save(newEx);
                                });

                        Industry industry = industryRepository.findByName("Unknown")
                                .orElseGet(() -> {
                                    log.info("湲곕낯 Industry (Unknown) ?앹꽦");
                                    Industry newInd = new Industry();
                                    newInd.setName("Unknown");
                                    return industryRepository.save(newInd);
                                });

                        Stock stock = stockRepository.findByExchangeAndStockCode(exchange, dto.getSymbol())
                                .orElse(new Stock());

                        stock.setStockCode(dto.getSymbol());
                        stock.setCompanyName(dto.getName());
                        stock.setExchange(exchange);
                        stock.setIndustry(industry);
                        String currency = resolveCurrencyCode(dto.getStock_exchange().getCountry_code(), finalExchangeCode);
                        if (currency != null) {
                            stock.setCurrency(currency);
                        }

                        stockRepository.save(stock);
                    }
                    log.info("Ticker ?숆린???꾨즺.");
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private static String resolveCurrencyCode(String countryCode, String exchangeCode) {
        if (countryCode != null) {
            String normalized = countryCode.trim().toUpperCase();
            switch (normalized) {
                case "KR":
                    return "KRW";
                case "US":
                    return "USD";
                case "JP":
                    return "JPY";
                case "CN":
                    return "CNY";
                case "HK":
                    return "HKD";
                case "GB":
                    return "GBP";
                case "EU":
                    return "EUR";
                default:
                    break;
            }
        }

        if (exchangeCode != null) {
            String normalized = exchangeCode.trim().toUpperCase();
            switch (normalized) {
                case "KRX":
                case "KOSDAQ":
                case "XKRX":
                case "XKOS":
                    return "KRW";
                case "NYSE":
                case "NASDAQ":
                case "XNYS":
                case "XNAS":
                    return "USD";
                default:
                    break;
            }
        }

        return null;
    }
}
