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
        String csvArg = Arrays.stream(args)
                .filter(a -> a != null && !a.isBlank())
                .filter(a -> !a.startsWith("--"))   // 스프링 옵션 제외
                .findFirst()
                .orElse(null);
        String exchangeArg = Arrays.stream(args)
                .filter(a -> a != null && a.startsWith("--exchange="))
                .map(a -> a.substring("--exchange=".length()))
                .findFirst()
                .orElse(null);

        if (csvArg == null) {
            log.error("CSV 경로를 인자로 넘겨주세요. ex) data/financials_top200_2020_2024.csv");
            return;
        }

        Path csvPath = Path.of(csvArg);
        log.info("재무제표 CSV import 시작: {}", csvPath);
        FinancialImportService.ImportResult result = importService.importFromCsv(csvPath, exchangeArg);
        log.info("?щТ?쒗몴 CSV import 寃곌낵: {}", result);
        log.info("재무제표 CSV import 완료");
    }

    /** 사용법
     * SPRING_PROFILES_ACTIVE=dev,import-financial-csv \
     * ./gradlew bootRun --args='data/financials_top200_2020_2024.csv'
     */
}
