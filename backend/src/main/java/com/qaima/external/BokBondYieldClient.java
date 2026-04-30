package com.qaima.external;

import com.qaima.dto.bok.BokStatisticSearchResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class BokBondYieldClient {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    private final WebClient webClient;

    @Value("${bok.api-key:${BOK_API_KEY:}}")
    private String apiKey;

    public BokBondYieldClient(@Qualifier("bokWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<BokStatisticSearchResponse.Row>> fetchDailyBondYieldSeries(
            String statCode,
            String itemCode,
            LocalDate from,
            LocalDate to
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("bok.api-key is missing"));
        }
        if (statCode == null || statCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("statCode is required"));
        }
        if (itemCode == null || itemCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("itemCode is required"));
        }
        if (from == null || to == null) {
            return Mono.error(new IllegalArgumentException("from and to are required"));
        }
        if (from.isAfter(to)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return fetchPage(statCode, itemCode, from, to, 1, PAGE_SIZE)
                .flatMap(firstResponse -> {
                    BokStatisticSearchResponse.Payload payload = firstResponse.getStatisticSearch();
                    int total = payload == null || payload.getListTotalCount() == null
                            ? rowsOf(firstResponse).size()
                            : payload.getListTotalCount();
                    int pageCount = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                    List<BokStatisticSearchResponse.Row> rows = new ArrayList<>(rowsOf(firstResponse));

                    Mono<List<BokStatisticSearchResponse.Row>> chain = Mono.just(rows);
                    for (int page = 2; page <= pageCount; page++) {
                        int start = ((page - 1) * PAGE_SIZE) + 1;
                        int end = page * PAGE_SIZE;
                        chain = chain.flatMap(acc -> fetchPage(statCode, itemCode, from, to, start, end)
                                .map(response -> {
                                    acc.addAll(rowsOf(response));
                                    return acc;
                                }));
                    }
                    return chain;
                })
                .doOnNext(rows -> log.info(
                        "[BOK] daily bond yield series fetched. statCode={}, itemCode={}, from={}, to={}, rows={}",
                        statCode, itemCode, from, to, rows.size()
                ));
    }

    private Mono<BokStatisticSearchResponse> fetchPage(
            String statCode,
            String itemCode,
            LocalDate from,
            LocalDate to,
            int start,
            int end
    ) {
        return webClient.get()
                .uri("/StatisticSearch/{apiKey}/json/kr/{start}/{end}/{statCode}/D/{from}/{to}/{itemCode}",
                        apiKey,
                        start,
                        end,
                        statCode,
                        from.format(BASIC_DATE),
                        to.format(BASIC_DATE),
                        itemCode)
                .retrieve()
                .bodyToMono(BokStatisticSearchResponse.class)
                .retryWhen(Retry.backoff(MAX_RETRIES, Duration.ofMillis(500))
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "[BOK] bond yield page retry. statCode={}, itemCode={}, from={}, to={}, start={}, end={}, attempt={}, cause={}",
                                statCode,
                                itemCode,
                                from,
                                to,
                                start,
                                end,
                                signal.totalRetries() + 1,
                                signal.failure().toString()
                        )));
    }

    private List<BokStatisticSearchResponse.Row> rowsOf(BokStatisticSearchResponse response) {
        BokStatisticSearchResponse.Payload payload = response == null ? null : response.getStatisticSearch();
        List<BokStatisticSearchResponse.Row> rows = payload == null ? null : payload.getRow();
        return rows == null ? List.of() : rows;
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return true;
    }
}
