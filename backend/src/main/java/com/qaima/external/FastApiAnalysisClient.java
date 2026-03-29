package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.qaima.dto.featone.FeatOneAnalysisMetricsDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FastApiAnalysisClient implements AnalysisApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FastApiAnalysisClient(
            @Qualifier("analysisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request) {

        return webClient.post()
                .uri("/api/v1/analysis/feature1")
                .bodyValue(request)
                .exchangeToMono(resp -> {
                    HttpStatusCode status = resp.statusCode();

                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                if (status.isError()) {
                                    log.error("[FastAPI] status={} body={}", status.value(), body);
                                    return Mono.error(new RuntimeException("FASTAPI_HTTP_" + status.value()));
                                }

                                try {
                                    JsonNode root = objectMapper.readTree(body);
                                    JsonNode dataNode = root.has("data") ? root.get("data") : root;
                                    if (dataNode == null || dataNode.isMissingNode() || dataNode.isNull()) {
                                        throw new IllegalStateException("FastAPI feature1 payload(data) is missing");
                                    }

                                    ObjectMapper snakeMapper = objectMapper.copy()
                                            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

                                    FeatOneAnalysisResponseDto camelParsed =
                                            objectMapper.treeToValue(dataNode, FeatOneAnalysisResponseDto.class);
                                    FeatOneAnalysisResponseDto snakeParsed =
                                            snakeMapper.treeToValue(dataNode, FeatOneAnalysisResponseDto.class);

                                    FeatOneAnalysisResponseDto dto = chooseBetter(camelParsed, snakeParsed);

                                    List<String> warnings = new ArrayList<>();
                                    JsonNode warningsNode = root.path("meta").path("warnings");
                                    if (!warningsNode.isArray()) {
                                        warningsNode = dataNode.path("meta").path("warnings");
                                    }
                                    if (warningsNode.isArray()) {
                                        warningsNode.forEach(node -> {
                                            if (node.isTextual()) warnings.add(node.asText());
                                        });
                                    }
                                    dto.setWarnings(warnings);

                                    log.info(
                                            "[FastAPI->Spring][Feature1] metrics fields stockCode={}, asOf={}, ohlcvSummary?={}, financialSummary?={}, indicatorSummary?={}, schemaVersion={}, indicators?={}",
                                            dto.getMetrics() != null ? dto.getMetrics().getStockCode() : null,
                                            dto.getMetrics() != null ? dto.getMetrics().getAsOf() : null,
                                            dto.getMetrics() != null && dto.getMetrics().getOhlcvSummary() != null,
                                            dto.getMetrics() != null && dto.getMetrics().getFinancialSummary() != null,
                                            dto.getMetrics() != null && dto.getMetrics().getIndicatorSummary() != null,
                                            dto.getMetrics() != null ? dto.getMetrics().getSchemaVersion() : null,
                                            dto.getMetrics() != null && dto.getMetrics().getIndicators() != null
                                    );

                                    return Mono.just(dto);
                                } catch (Exception e) {
                                    log.error("[FastAPI] decode failed. body={}", body, e);
                                    return Mono.error(e);
                                }
                            });
                });
    }

    private FeatOneAnalysisResponseDto chooseBetter(
            FeatOneAnalysisResponseDto camelParsed,
            FeatOneAnalysisResponseDto snakeParsed
    ) {
        int camelScore = score(camelParsed);
        int snakeScore = score(snakeParsed);
        return camelScore >= snakeScore ? camelParsed : snakeParsed;
    }

    private int score(FeatOneAnalysisResponseDto dto) {
        if (dto == null) return 0;
        FeatOneAnalysisMetricsDto m = dto.getMetrics();
        if (m == null) return 0;

        int s = 0;
        if (m.getStockCode() != null) s++;
        if (m.getAsOf() != null) s++;
        if (m.getOhlcvSummary() != null) s++;
        if (m.getFinancialSummary() != null) s++;
        if (m.getIndicatorSummary() != null) s++;
        if (m.getSchemaVersion() != null) s++;
        if (m.getIndicators() != null) s++;
        return s;
    }
}
