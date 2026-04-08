package com.qaima.importer;

import com.qaima.domain.Stock;
import com.qaima.repository.StockRepository;
import com.qaima.service.feature2.NewsSentimentService;
import com.qaima.service.feature2.NewsSentimentService.NewsDatasetExportRow;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;



//gradlew.bat bootRun --args="--batch.news-export.enabled=true
// --batch.news-export.per-stock=5 --batch.news-export.output=C:\qaima\data\news_dataset_1.csv"
// export.per-stock=5  -> 기사 수 5개 추출

//gradlew.bat bootRun --args="--batch.news-export.enabled=true
// --batch.news-export.stock-codes=377330 --batch.news-export.per-stock=20
// --batch.news-export.output=C:\qaima\data\news_dataset_sample.csv" -> 특정 종목 기사 추출
        @Component
@RequiredArgsConstructor
@Slf4j
public class NewsDatasetExportJob implements ApplicationRunner {

    private static final int DEFAULT_RANDOM_STOCK_COUNT = 100; // 랜덤 종목 코드 추출 개수

    private final NewsDatasetExportProperties properties;
    private final StockRepository stockRepository;
    private final NewsSentimentService newsSentimentService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }

        List<String> stockCodes = resolveStockCodes();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("batch.news-export.stock-codes is empty and random stock selection returned no result");
        }
        if (properties.getOutput() == null || properties.getOutput().isBlank()) {
            throw new IllegalStateException("batch.news-export.output is required");
        }
        if (properties.getPerStock() <= 0) {
            throw new IllegalStateException("batch.news-export.per-stock must be positive");
        }

        Path outputPath = Paths.get(properties.getOutput()).toAbsolutePath();
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        log.info("[NewsDatasetExportJob] export start. stocks={}, perStock={}, output={}",
                stockCodes.size(), properties.getPerStock(), outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8
        )) {
            writer.write("stock_code,focus,detail,label,source,content_uri");
            writer.newLine();

            for (String stockCode : stockCodes) {
                exportStock(writer, stockCode);
            }
        }

        log.info("[NewsDatasetExportJob] export finished. output={}", outputPath);
    }

    private void exportStock(BufferedWriter writer, String stockCode) throws IOException {
        Stock stock = stockRepository.findByStockCodeWithExchangeAndIndustry(stockCode).orElse(null);
        if (stock == null) {
            log.warn("[NewsDatasetExportJob] stock not found. stockCode={}", stockCode);
            return;
        }

        List<String> warnings = new ArrayList<>();
        List<NewsDatasetExportRow> rows =
                newsSentimentService.collectRelevantArticlesForDataset(stock, properties.getPerStock(), warnings);

        for (NewsDatasetExportRow row : rows) {
            writer.write(csv(row.stockCode()));
            writer.write(',');
            writer.write(csv(row.focus()));
            writer.write(',');
            writer.write(csv(row.detail()));
            writer.write(',');
            writer.write(csv(row.label()));
            writer.write(',');
            writer.write(csv(row.source()));
            writer.write(',');
            writer.write(csv(row.contentUri()));
            writer.newLine();
        }

        if (!warnings.isEmpty()) {
            log.info("[NewsDatasetExportJob] stock export done. stockCode={}, rows={}, warnings={}",
                    stockCode, rows.size(), warnings);
        } else {
            log.info("[NewsDatasetExportJob] stock export done. stockCode={}, rows={}", stockCode, rows.size());
        }
    }

    private List<String> normalizeStockCodes(List<String> stockCodes) {
        if (stockCodes == null || stockCodes.isEmpty()) {
            return List.of();
        }
        return stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> resolveStockCodes() {
        List<String> configuredStockCodes = normalizeStockCodes(properties.getStockCodes());
        if (!configuredStockCodes.isEmpty()) {
            return configuredStockCodes;
        }

        List<String> randomStockCodes = normalizeStockCodes(
                stockRepository.findRandomStockCodes(DEFAULT_RANDOM_STOCK_COUNT)
        );
        log.info("[NewsDatasetExportJob] using random stock codes. count={}, stockCodes={}",
                randomStockCodes.size(), randomStockCodes);
        return randomStockCodes;
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
