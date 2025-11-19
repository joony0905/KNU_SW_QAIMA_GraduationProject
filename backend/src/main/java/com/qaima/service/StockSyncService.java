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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {

    private final StockRepository stockRepository;
    private final ExchangeRepository exchangeRepository;
    private final IndustryRepository industryRepository;

    @Transactional
    public void syncMarketStackTickers(List<TickerData> tickersFromApi) {

        log.info("Marketstack Ticker 동기화 시작. (총 {}건)", tickersFromApi.size());

        for (TickerData dto : tickersFromApi) {
            if (dto.getStock_exchange() == null) {
                log.warn("Exchange 정보가 없는 Ticker입니다: {}", dto.getSymbol());
                continue;
            }

            String exchangeCodeValue = dto.getStock_exchange().getAcronym();

            if (exchangeCodeValue == null) {
                exchangeCodeValue = dto.getStock_exchange().getMic();
            }

            final String finalExchangeCode = exchangeCodeValue;

            Exchange exchange = exchangeRepository.findByCode(finalExchangeCode)
                    .orElseGet(() -> {
                        log.info("새로운 Exchange 생성: {}", finalExchangeCode);
                        Exchange newEx = new Exchange();
                        newEx.setCode(finalExchangeCode);
                        newEx.setName(dto.getStock_exchange().getName());
                        newEx.setCountry(dto.getStock_exchange().getCountry());
                        return exchangeRepository.save(newEx);
                    });

            Industry industry = industryRepository.findByName("Unknown")
                    .orElseGet(() -> {
                        log.info("기본 Industry (Unknown) 생성");
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

            stockRepository.save(stock);
        }
        log.info("Ticker 동기화 완료.");
    }
}
