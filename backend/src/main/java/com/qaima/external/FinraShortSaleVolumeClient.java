package com.qaima.external;

import com.qaima.dto.finra.FinraShortSaleVolumeRow;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FinraShortSaleVolumeClient {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final WebClient webClient;
    private final FinraShortSaleVolumeParser parser;

    public FinraShortSaleVolumeClient(
            @Qualifier("finraWebClient") WebClient webClient,
            FinraShortSaleVolumeParser parser
    ) {
        this.webClient = webClient;
        this.parser = parser;
    }

    public Mono<List<FinraShortSaleVolumeRow>> fetchConsolidatedNmsDaily(LocalDate date) {
        if (date == null) {
            return Mono.error(new IllegalArgumentException("date is required"));
        }

        String fileDate = FILE_DATE.format(date);
        String path = "/equity/regsho/daily/CNMSshvol" + fileDate + ".txt";
        return webClient.get()
                .uri(path)
                .exchangeToMono(response -> {
                    int statusCode = response.statusCode().value();
                    if (isMissingFileStatus(statusCode)) {
                        log.info("[FINRA] CNMS daily short volume file unavailable. date={}, status={}",
                                date, statusCode);
                        return Mono.just(List.of());
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(String.class)
                            .map(parser::parse)
                            .defaultIfEmpty(List.of());
                })
                .doOnNext(rows -> log.info("[FINRA] CNMS daily short volume fetched. date={}, rows={}",
                        date, rows.size()));
    }

    static boolean isMissingFileStatus(int statusCode) {
        return statusCode == 403 || statusCode == 404;
    }
}
