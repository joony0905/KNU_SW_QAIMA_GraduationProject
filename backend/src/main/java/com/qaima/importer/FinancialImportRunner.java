package com.qaima.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;

@Slf4j
@Component
@Profile("import-financial-csv")
@RequiredArgsConstructor
public class FinancialImportRunner implements CommandLineRunner {

    private final FinancialImportService importService;

    @Override
    public void run(String... args) throws Exception {
        String exchangeArg = Arrays.stream(args)
                .filter(a -> a != null && a.startsWith("--exchange="))
                .map(a -> a.substring("--exchange=".length()))
                .findFirst()
                .orElse(null);

        String csvArg = Arrays.stream(args)
                .filter(a -> a != null && !a.isBlank())
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV path is required. e.g. data/financials_kospi_2020_2024.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("Financial CSV import start: {} (exchange={})", csvPath, exchangeArg);
        importService.importFromCsv(csvPath, exchangeArg);
        log.info("Financial CSV import done");
    }

    /** 사용법
     * SPRING_PROFILES_ACTIVE=dev,import-financial-csv \
     * ./gradlew bootRun --args='data/financials_kospi_2020_2024.csv --exchange=KOSPI'
     */
}
