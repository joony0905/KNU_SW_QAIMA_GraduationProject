package com.qaima.external;

import com.qaima.dto.bok.BokStatisticSearchResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BokExchangeRateClient {

    private final BokBondYieldClient bokBondYieldClient;

    public Mono<java.util.List<BokStatisticSearchResponse.Row>> fetchDailyExchangeRateSeries(
            String statCode,
            String itemCode,
            LocalDate from,
            LocalDate to
    ) {
        return bokBondYieldClient.fetchDailyBondYieldSeries(statCode, itemCode, from, to);
    }
}
