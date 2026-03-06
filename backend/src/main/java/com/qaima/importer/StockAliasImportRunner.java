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
@Profile("import-stock-alias")
@RequiredArgsConstructor
public class StockAliasImportRunner implements CommandLineRunner {

    private final StockAliasImportService importService;

    @Override
    public void run(String... args) throws Exception {
        String csvArg = Arrays.stream(args)
                .filter(arg -> arg != null && !arg.isBlank())
                .filter(arg -> !arg.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV path is required. ex) data/stock_alias_seed.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("stock alias CSV import start: {}", csvPath);
        importService.importFromCsv(csvPath);
        log.info("stock alias CSV import complete");
    }
}
