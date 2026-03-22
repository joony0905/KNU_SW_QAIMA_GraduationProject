package com.qaima.importer;

import java.nio.file.Path;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("import-short-selling-csv")
@RequiredArgsConstructor
public class ShortSellingImportRunner implements CommandLineRunner {

    private final ShortSellingImportService importService;

    @Override
    public void run(String... args) throws Exception {
        String csvArg = Arrays.stream(args)
                .filter(arg -> arg != null && !arg.isBlank())
                .filter(arg -> !arg.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV path is required. ex) backend/financial_data/short_selling_KOSPI_KOSDAQ_KONEX_2025-03-21_2026-03-20.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("Short selling CSV import start: {}", csvPath);
        importService.importFromCsv(csvPath);
        log.info("Short selling CSV import complete");
    }
}
