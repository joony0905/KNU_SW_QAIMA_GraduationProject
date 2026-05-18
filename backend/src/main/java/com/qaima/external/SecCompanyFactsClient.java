package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.qaima.dto.sec.SecIssuedSharesFact;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SecCompanyFactsClient {

    private final WebClient webClient;
    private final SecCompanyFactsParser parser;
    private final String dataBaseUrl;

    public SecCompanyFactsClient(
            @Qualifier("secWebClient") WebClient webClient,
            SecCompanyFactsParser parser,
            @Value("${sec.edgar.data-base-url:https://data.sec.gov}") String dataBaseUrl
    ) {
        this.webClient = webClient;
        this.parser = parser;
        this.dataBaseUrl = dataBaseUrl;
    }

    public Mono<Optional<SecIssuedSharesFact>> fetchLatestCommonSharesOutstanding(String cik) {
        String cik10 = cik10(cik);
        if (cik10 == null) {
            return Mono.error(new IllegalArgumentException("SEC CIK is required"));
        }

        return webClient.get()
                .uri(dataBaseUrl + "/api/xbrl/companyfacts/CIK" + cik10 + ".json")
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.just(Optional.<SecIssuedSharesFact>empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(JsonNode.class)
                            .map(root -> parser.parseLatestCommonSharesOutstanding(cik10, root));
                })
                .doOnNext(fact -> log.info("[SEC] companyfacts issued shares fetched. cik={}, found={}",
                        cik10, fact.isPresent()));
    }

    private String cik10(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }
        return String.format("%010d", Long.parseLong(digits));
    }
}
