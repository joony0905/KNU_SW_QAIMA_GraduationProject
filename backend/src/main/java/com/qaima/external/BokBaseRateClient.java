package com.qaima.external;

import com.qaima.dto.bok.BokKeyStatisticListResponse;
import com.qaima.dto.bok.BokStatisticSearchResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class BokBaseRateClient {

    private static final String KEY_STAT_NAME = "\uD55C\uAD6D\uC740\uD589 \uAE30\uC900\uAE08\uB9AC";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final WebClient webClient;

    @Value("${bok.api-key:${BOK_API_KEY:}}")
    private String apiKey;

    public BokBaseRateClient(@Qualifier("bokWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<BokKeyStatisticListResponse.Row> fetchLatestBaseRate() {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("bok.api-key is missing"));
        }

        return webClient.get()
                .uri("/KeyStatisticList/{apiKey}/json/kr/1/200", apiKey)
                .retrieve()
                .bodyToMono(BokKeyStatisticListResponse.class)
                .map(BokKeyStatisticListResponse::getKeyStatisticList)
                .map(payload -> payload == null ? List.<BokKeyStatisticListResponse.Row>of() : payload.getRow())
                .map(rows -> rows == null ? List.<BokKeyStatisticListResponse.Row>of() : rows)
                .map(rows -> rows.stream()
                        .filter(row -> KEY_STAT_NAME.equals(row.getKeyStatName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("BOK key statistic row not found: " + KEY_STAT_NAME)))
                .doOnNext(row -> log.info("[BOK] latest base rate fetched. cycle={}, value={}",
                        row.getCycle(), row.getDataValue()));
    }

    public Mono<List<BokStatisticSearchResponse.Row>> fetchDailyBaseRateSeries(LocalDate from, LocalDate to) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("bok.api-key is missing"));
        }
        if (from == null || to == null) {
            return Mono.error(new IllegalArgumentException("from and to are required"));
        }
        if (from.isAfter(to)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return webClient.get()
                .uri("/StatisticSearch/{apiKey}/json/kr/1/10000/722Y001/D/{from}/{to}/0101000",
                        apiKey,
                        from.format(BASIC_DATE),
                        to.format(BASIC_DATE))
                .retrieve()
                .bodyToMono(BokStatisticSearchResponse.class)
                .map(BokStatisticSearchResponse::getStatisticSearch)
                .map(payload -> payload == null ? List.<BokStatisticSearchResponse.Row>of() : payload.getRow())
                .map(rows -> rows == null ? List.<BokStatisticSearchResponse.Row>of() : rows)
                .doOnNext(rows -> log.info("[BOK] daily base rate series fetched. from={}, to={}, rows={}",
                        from, to, rows.size()));
    }
}
