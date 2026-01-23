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
@Profile("import-stock-alias")
@RequiredArgsConstructor
public class StockAliasImportRunner implements CommandLineRunner {

    private final StockAliasImportService importService;

    @Override
    public void run(String... args) throws Exception {
        String csvArg = Arrays.stream(args)
                .filter(a -> a != null && !a.isBlank())
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV 경로를 인자로 넘겨주세요. ex) data/stock_alias_seed.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("종목 alias CSV import 시작: {}", csvPath);
        importService.importFromCsv(csvPath);
        log.info("종목 alias CSV import 완료");
    }
}
