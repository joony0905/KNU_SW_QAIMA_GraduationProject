package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneAnalysisExplainDto;
import com.qaima.dto.featone.FeatOneAnalysisExplainOverallDto;
import com.qaima.dto.featone.FeatOneAnalysisExplainSectionDto;
import com.qaima.dto.featone.FeatOneAnalysisExplainSectionsDto;
import com.qaima.dto.featone.FeatOneAnalysisMetricsDto;
import com.qaima.dto.featone.FeatOneFinancialSeriesDto;
import com.qaima.dto.featone.FeatOneInternalAnalysisRequestDto;
import com.qaima.dto.featone.FeatOneMarketSnapshotDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import com.qaima.dto.feature2.Feature2ExplainRequestDto;
import com.qaima.dto.feature2.Feature2ExplainResponseDto;
import com.qaima.dto.feature2.Feature2InternalAnalysisRequestDto;
import com.qaima.external.dto.feature3.Feature3FastApiAnalyzeRequestDto;
import com.qaima.external.dto.feature3.Feature3FastApiAnalyzeResponseDto;
import com.qaima.external.dto.feature3.Feature3InternalAnalysisRequestDto;
import com.qaima.dto.indicator.IndicatorBundleDto;
import com.qaima.dto.indicator.IndicatorSpecDto;
import com.qaima.dto.ohlcv.OhlcvSummaryDto;
import com.qaima.external.dto.feature1.Feature1InboundExplainDto;
import com.qaima.external.dto.feature1.Feature1InboundExplainOverallDto;
import com.qaima.external.dto.feature1.Feature1InboundExplainSectionDto;
import com.qaima.external.dto.feature1.Feature1InboundExplainSectionsDto;
import com.qaima.external.dto.feature1.Feature1InboundIndicatorBundleDto;
import com.qaima.external.dto.feature1.Feature1InboundIndicatorSpecDto;
import com.qaima.external.dto.feature1.Feature1InboundMetricsDto;
import com.qaima.external.dto.feature1.Feature1InboundOhlcvSummaryDto;
import com.qaima.external.dto.feature1.Feature1InboundResponseDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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
    private final ObjectMapper snakeCaseObjectMapper;

    public FastApiAnalysisClient(
            @Qualifier("analysisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.snakeCaseObjectMapper = objectMapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Override
    public Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request) {
        return postForPayload("/feature1/analysis", FeatOneInternalAnalysisRequestDto.from(request), "feature1")
                .flatMap(envelope -> {
                    try {
                        Feature1InboundResponseDto inbound = snakeCaseResponseValue(envelope.payloadNode());
                        FeatOneAnalysisResponseDto dto = toResponseDto(inbound);
                        validateBoundResponse(dto, envelope.payloadNode());
                        dto.setWarnings(readWarnings(envelope.rootNode(), envelope.payloadNode()));
                        log.info(
                                "[FastAPI->Spring][Feature1] metrics fields stockCode={}, asOf={}, ohlcvSummary?={}, financialSeries?={}, marketSnapshot?={}, indicatorSummary?={}, schemaVersion={}, indicators?={}",
                                dto.getMetrics() != null ? dto.getMetrics().getStockCode() : null,
                                dto.getMetrics() != null ? dto.getMetrics().getAsOf() : null,
                                dto.getMetrics() != null && dto.getMetrics().getOhlcvSummary() != null,
                                dto.getMetrics() != null && dto.getMetrics().getFinancialSeries() != null,
                                dto.getMetrics() != null && dto.getMetrics().getMarketSnapshot() != null,
                                dto.getMetrics() != null && dto.getMetrics().getIndicatorSummary() != null,
                                dto.getMetrics() != null ? dto.getMetrics().getSchemaVersion() : null,
                                dto.getMetrics() != null && dto.getMetrics().getIndicators() != null
                        );
                        return Mono.just(dto);
                    } catch (Exception e) {
                        log.error("[FastApiAnalysisClient][feature1] decode failed. body={}", envelope.body(), e);
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<Feature2ExplainResponseDto> requestFeature2Explain(Feature2ExplainRequestDto request) {
        return postForPayload("/feature2/analysis", Feature2InternalAnalysisRequestDto.from(request), "feature2/analysis")
                .flatMap(envelope -> {
                    try {
                        Feature2ExplainResponseDto dto =
                                snakeCaseObjectMapper.treeToValue(envelope.payloadNode(), Feature2ExplainResponseDto.class);
                        if (dto.getWarnings() == null) {
                            dto.setWarnings(readWarnings(envelope.rootNode(), envelope.payloadNode()));
                        }
                        return Mono.just(dto);
                    } catch (Exception e) {
                        log.error("[FastApiAnalysisClient][feature2-explain] decode failed. body={}", envelope.body(), e);
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<Feature3FastApiAnalyzeResponseDto> requestPortfolioAnalysis(Feature3FastApiAnalyzeRequestDto request) {
        return postForPayload("/feature3/analysis", Feature3InternalAnalysisRequestDto.from(request), "feature3/analysis")
                .flatMap(envelope -> {
                    try {
                        Feature3FastApiAnalyzeResponseDto dto =
                                snakeCaseObjectMapper.treeToValue(envelope.payloadNode(), Feature3FastApiAnalyzeResponseDto.class);
                        return Mono.just(dto);
                    } catch (Exception e) {
                        log.error("[FastApiAnalysisClient][feature3-analysis] decode failed. body={}", envelope.body(), e);
                        return Mono.error(e);
                    }
                });
    }

    private Feature1InboundResponseDto snakeCaseResponseValue(JsonNode payloadNode) throws com.fasterxml.jackson.core.JsonProcessingException {
        return snakeCaseObjectMapper.treeToValue(payloadNode, Feature1InboundResponseDto.class);
    }

    private Mono<FastApiPayloadEnvelope> postForPayload(String uri, Object request, String endpointName) {
        JsonNode snakePayloadNode = toSnakeCaseNode(request);
        String errorCodeEndpoint = endpointName.replace('/', '_').replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        log.info("[FastApiAnalysisClient][{}][request] uri={}, body={}", endpointName, uri, snakePayloadNode);

        return webClient.post()
                .uri(uri)
                .bodyValue(snakePayloadNode)
                .exchangeToMono(resp -> {
                    HttpStatusCode status = resp.statusCode();
                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                if (status.isError()) {
                                    log.error("[FastApiAnalysisClient][{}][error] status={}, body={}",
                                            endpointName, status.value(), body);
                                    return Mono.error(new RuntimeException("FASTAPI_" + errorCodeEndpoint + "_HTTP_" + status.value()));
                                }
                                try {
                                    log.info("[FastApiAnalysisClient][{}] raw response={}", endpointName, body);
                                    JsonNode root = objectMapper.readTree(body);
                                    JsonNode payloadNode = extractPayload(root, endpointName);
                                    log.info("[FastApiAnalysisClient][{}] payload extract result={}", endpointName, payloadNode);
                                    return Mono.just(new FastApiPayloadEnvelope(root, payloadNode, body));
                                } catch (Exception e) {
                                    log.error("[FastApiAnalysisClient][{}] payload decode failed. body={}", endpointName, body, e);
                                    return Mono.error(e);
                                }
                            });
                });
    }

    private record FastApiPayloadEnvelope(JsonNode rootNode, JsonNode payloadNode, String body) {
    }

    private FeatOneAnalysisResponseDto toResponseDto(Feature1InboundResponseDto inbound) {
        if (inbound == null) {
            throw new IllegalStateException("FastAPI feature1 inbound response is null");
        }

        return FeatOneAnalysisResponseDto.builder()
                .metrics(toMetricsDto(inbound.getMetrics()))
                .explain(toExplainDto(inbound.getExplain()))
                .warnings(inbound.getWarnings())
                .build();
    }

    private FeatOneAnalysisMetricsDto toMetricsDto(Feature1InboundMetricsDto inbound) {
        if (inbound == null) {
            throw new IllegalStateException("FastAPI feature1 inbound metrics is null");
        }

        return FeatOneAnalysisMetricsDto.builder()
                .stockCode(inbound.getStockCode())
                .asOf(inbound.getAsOf())
                .ohlcvSummary(toOhlcvSummaryDto(inbound.getOhlcvSummary()))
                .financialSeries(inbound.getFinancialSeries())
                .marketSnapshot(inbound.getMarketSnapshot())
                .indicators(toIndicatorBundleDto(inbound.getIndicators()))
                .indicatorSummary(inbound.getIndicatorSummary())
                .schemaVersion(inbound.getSchemaVersion())
                .build();
    }

    private OhlcvSummaryDto toOhlcvSummaryDto(Feature1InboundOhlcvSummaryDto inbound) {
        if (inbound == null) {
            return null;
        }
        return OhlcvSummaryDto.builder()
                .count(inbound.getCount())
                .from(inbound.getFrom())
                .to(inbound.getTo())
                .lastClose(inbound.getLastClose())
                .build();
    }

    private IndicatorBundleDto toIndicatorBundleDto(Feature1InboundIndicatorBundleDto inbound) {
        if (inbound == null) {
            return null;
        }
        return IndicatorBundleDto.builder()
                .spec(toIndicatorSpecDto(inbound.getSpec()))
                .ema(inbound.getEma())
                .bb20_2(inbound.getBb20_2())
                .stoch14_3_3(inbound.getStoch14_3_3())
                .warnings(inbound.getWarnings() != null ? inbound.getWarnings() : Collections.emptyList())
                .build();
    }

    private IndicatorSpecDto toIndicatorSpecDto(Feature1InboundIndicatorSpecDto inbound) {
        if (inbound == null) {
            return null;
        }
        return IndicatorSpecDto.builder()
                .emaPeriod(inbound.getEmaPeriod())
                .bollingerPeriod(inbound.getBollingerPeriod())
                .bollingerStdDev(inbound.getBollingerStdDev())
                .stochasticKPeriod(inbound.getStochasticKPeriod())
                .stochasticDPeriod(inbound.getStochasticDPeriod())
                .stochasticSmooth(inbound.getStochasticSmooth())
                .build();
    }

    private FeatOneAnalysisExplainDto toExplainDto(Feature1InboundExplainDto inbound) {
        if (inbound == null) {
            return null;
        }
        return FeatOneAnalysisExplainDto.builder()
                .provider(inbound.getProvider())
                .model(inbound.getModel())
                .text(inbound.getText())
                .sections(toExplainSectionsDto(inbound.getSections()))
                .overall(toExplainOverallDto(inbound.getOverall()))
                .warnings(inbound.getWarnings())
                .build();
    }

    private FeatOneAnalysisExplainSectionsDto toExplainSectionsDto(Feature1InboundExplainSectionsDto inbound) {
        if (inbound == null) {
            return null;
        }
        return FeatOneAnalysisExplainSectionsDto.builder()
                .priceFlow(toExplainSectionDto(inbound.getPriceFlow()))
                .marketSnapshot(toExplainSectionDto(inbound.getMarketSnapshot()))
                .indicators(toExplainSectionDto(inbound.getIndicators()))
                .financialTimeline(toExplainSectionDto(inbound.getFinancialTimeline()))
                .build();
    }

    private FeatOneAnalysisExplainOverallDto toExplainOverallDto(Feature1InboundExplainOverallDto inbound) {
        if (inbound == null) {
            return null;
        }
        return FeatOneAnalysisExplainOverallDto.builder()
                .summary(inbound.getSummary())
                .bullets(inbound.getBullets())
                .risks(inbound.getRisks())
                .conclusion(inbound.getConclusion())
                .build();
    }

    private FeatOneAnalysisExplainSectionDto toExplainSectionDto(Feature1InboundExplainSectionDto inbound) {
        if (inbound == null) {
            return null;
        }
        return FeatOneAnalysisExplainSectionDto.builder()
                .title(inbound.getTitle())
                .summary(inbound.getSummary())
                .bullets(inbound.getBullets())
                .build();
    }

    private void validateBoundResponse(FeatOneAnalysisResponseDto dto, JsonNode payloadNode) {
        if (dto == null) {
            throw new IllegalStateException("FastAPI feature1 DTO binding returned null response");
        }
        if (dto.getMetrics() == null) {
            throw new IllegalStateException("FastAPI feature1 DTO binding returned null metrics");
        }

        boolean allMetricsCoreFieldsNull =
                dto.getMetrics().getStockCode() == null
                        && dto.getMetrics().getAsOf() == null
                        && dto.getMetrics().getOhlcvSummary() == null
                        && dto.getMetrics().getFinancialSeries() == null
                        && dto.getMetrics().getMarketSnapshot() == null
                        && dto.getMetrics().getIndicators() == null
                        && dto.getMetrics().getIndicatorSummary() == null
                        && dto.getMetrics().getSchemaVersion() == null;

        if (allMetricsCoreFieldsNull) {
            throw new IllegalStateException(
                    "FastAPI feature1 DTO binding produced empty metrics. payload.metrics keys="
                            + StreamSupport.stream(
                                    java.util.Spliterators.spliteratorUnknownSize(
                                            payloadNode.path("metrics").fieldNames(),
                                            0
                                    ),
                                    false
                            ).collect(Collectors.toList())
            );
        }
    }

    private JsonNode extractPayload(JsonNode root, String endpointName) {
        if (root == null || root.isNull() || root.isMissingNode() || !root.isObject()) {
            throw new IllegalStateException("FastAPI " + endpointName + " payload root is missing or not object");
        }
        if (root.has("data")) {
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull() || dataNode.isMissingNode() || !dataNode.isObject()) {
                throw new IllegalStateException("FastAPI " + endpointName + " envelope data is missing or not object");
            }
            return dataNode;
        }
        return root;
    }

    private List<String> readWarnings(JsonNode root, JsonNode payloadNode) {
        List<String> warnings = new ArrayList<>();
        appendWarnings(warnings, root.path("meta").path("warnings"));
        appendWarnings(warnings, payloadNode.path("meta").path("warnings"));
        appendWarnings(warnings, payloadNode.path("warnings"));
        return warnings;
    }

    private void appendWarnings(List<String> warnings, JsonNode warningsNode) {
        if (!warningsNode.isArray()) {
            return;
        }
        warningsNode.forEach(node -> {
            if (node.isTextual()) {
                String warning = node.asText();
                if (!warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
        });
    }

    private JsonNode toSnakeCaseNode(Object value) {
        JsonNode node = objectMapper.valueToTree(value);
        return toSnakeCaseNodeRecursive(node);
    }

    private JsonNode toSnakeCaseNodeRecursive(JsonNode node) {
        if (node == null || node.isNull()) return node;

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, toSnakeCaseNodeRecursive(arr.get(i)));
            }
            return arr;
        }

        if (!node.isObject()) {
            return node;
        }

        ObjectNode src = (ObjectNode) node;
        ObjectNode dst = objectMapper.createObjectNode();

        Iterator<String> fieldNames = src.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            String snakeKey = camelToSnake(key);
            dst.set(snakeKey, toSnakeCaseNodeRecursive(src.get(key)));
        }

        return dst;
    }

    private String camelToSnake(String key) {
        if (key == null || key.isEmpty()) return key;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

}
