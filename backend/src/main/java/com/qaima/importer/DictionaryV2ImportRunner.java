package com.qaima.importer;

import java.nio.file.Path;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("import-dictionary-v2")
@RequiredArgsConstructor
public class DictionaryV2ImportRunner implements CommandLineRunner {

    private final DictionaryV2ImportService importService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        String csvArg = Arrays.stream(args)
                .filter(arg -> arg != null && !arg.isBlank())
                .filter(arg -> !arg.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV path is required. e.g. ../data/dictionary_v2.csv");
            return;
        }

        boolean dryRun = Arrays.stream(args)
                .filter(arg -> arg != null && !arg.isBlank())
                .noneMatch(arg -> "--apply".equalsIgnoreCase(arg) || "--dry-run=false".equalsIgnoreCase(arg));

        Path csvPath = Path.of(csvArg);
        log.info("Dictionary v2 CSV import start: path={}, dryRun={}", csvPath, dryRun);
        DictionaryV2ImportService.ImportSummary summary = importService.importFromCsv(csvPath, dryRun);
        log.info("Dictionary v2 CSV import done: {}", summary.toLogString());
        SpringApplication.exit(applicationContext, () -> 0);
    }
}
