package com.qaima.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@Profile("import-financial-csv")
@RequiredArgsConstructor
public class FinancialImportRunner implements CommandLineRunner {

    private final FinancialImportService importService;

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            log.error("CSV 경로를 인자로 넘겨주세요. ex) --args=financial_005930_2019_2023.csv");
            return;
        }

        Path csvPath = Path.of(args[0]);
        log.info("재무제표 CSV import 시작: {}", csvPath);
        importService.importFromCsv(csvPath);
        log.info("재무제표 CSV import 완료");

    }
}

/** 사용법
 * csv를 파일 경로에 맞춰서 넣고 터미널에서도 똑같이 경로를 맞춘 후 아래 2줄의 코드를 터미널에서 실행
 * SPRING_PROFILES_ACTIVE=import-financial-csv \
 * ./gradlew bootRun --args='data/financial_005930_2019_2023.csv'
 */