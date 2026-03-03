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
@Profile("import-dictionary-csv")
@RequiredArgsConstructor
public class DictionaryImportRunner implements CommandLineRunner {

    private final DictionaryImportService importService;

    @Override
    public void run(String... args) throws Exception {
        String csvArg = Arrays.stream(args)
                .filter(arg -> arg != null && !arg.isBlank())
                .filter(arg -> !arg.startsWith("--"))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV path is required. e.g. data/dictionary_seed_sample.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("Dictionary CSV import start: {}", csvPath);
        importService.importFromCsv(csvPath);
        log.info("Dictionary CSV import done");
    }
}
