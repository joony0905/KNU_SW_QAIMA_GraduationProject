package com.qaima.external;

import com.qaima.common.Blocking;
import com.qaima.dto.sec.SecCompanyTickerExchangeEntry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SecCompanyTickerExchangeClient {

    private final WebClient webClient;
    private final SecCompanyTickerExchangeParser parser;

    public SecCompanyTickerExchangeClient(
            @Qualifier("secWebClient") WebClient webClient,
            SecCompanyTickerExchangeParser parser
    ) {
        this.webClient = webClient;
        this.parser = parser;
    }

    public Mono<List<SecCompanyTickerExchangeEntry>> fetchCompanyTickerExchange() {
        return webClient.get()
                .uri("/files/company_tickers_exchange.json")
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(text -> Blocking.call(() -> parser.parse(text)))
                .doOnNext(entries -> log.info("[SEC] company ticker exchange fetched. entries={}", entries.size()));
    }
}
