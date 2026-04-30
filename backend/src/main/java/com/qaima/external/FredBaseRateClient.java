package com.qaima.external;

import com.qaima.dto.fred.FredObservationsResponse;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FredBaseRateClient {

    private final WebClient webClient;

    @Value("${fred.api-key:${FRED_API_KEY:}}")
    private String apiKey;

    public FredBaseRateClient(@Qualifier("fredWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<java.util.List<FredObservationsResponse.Observation>> fetchSeriesObservations(
            String seriesId,
            LocalDate from,
            LocalDate to
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.error(new IllegalStateException("fred.api-key is missing"));
        }
        if (seriesId == null || seriesId.isBlank()) {
            return Mono.error(new IllegalArgumentException("seriesId is required"));
        }
        if (from == null || to == null) {
            return Mono.error(new IllegalArgumentException("from and to are required"));
        }
        if (from.isAfter(to)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fred/series/observations")
                        .queryParam("series_id", seriesId)
                        .queryParam("api_key", apiKey)
                        .queryParam("file_type", "json")
                        .queryParam("observation_start", from)
                        .queryParam("observation_end", to)
                        .build())
                .retrieve()
                .bodyToMono(FredObservationsResponse.class)
                .map(response -> response == null || response.getObservations() == null
                        ? java.util.List.<FredObservationsResponse.Observation>of()
                        : response.getObservations())
                .doOnNext(rows -> log.info(
                        "[FRED] base rate observations fetched. seriesId={}, from={}, to={}, rows={}",
                        seriesId,
                        from,
                        to,
                        rows.size()
                ));
    }
}
