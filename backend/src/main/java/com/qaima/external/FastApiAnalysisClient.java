package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import java.util.ArrayList;
import java.util.Iterator;
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

    private static final String INDICATORS_KEY = "indicators";

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
                .bodyValue(toSnakeCaseNode(request))
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
                                    JsonNode payloadNode = requirePayloadOnlyRoot(root, "feature1");

                                    JsonNode canonicalPayload = toCanonicalCamelNode(payloadNode.deepCopy());
                                    FeatOneAnalysisResponseDto dto =
                                            objectMapper.treeToValue(canonicalPayload, FeatOneAnalysisResponseDto.class);

                                    dto.setWarnings(readWarnings(canonicalPayload));

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

    private JsonNode requirePayloadOnlyRoot(JsonNode root, String endpointName) {
        if (root == null || root.isNull() || root.isMissingNode() || !root.isObject()) {
            throw new IllegalStateException("FastAPI " + endpointName + " payload root is missing or not object");
        }
        if (root.has("data")) {
            throw new IllegalStateException("FastAPI " + endpointName + " returned envelope(data), but payload-only is required");
        }
        return root;
    }

    private List<String> readWarnings(JsonNode canonicalPayload) {
        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = canonicalPayload.path("warnings");
        if (warningsNode.isArray()) {
            warningsNode.forEach(node -> {
                if (node.isTextual()) warnings.add(node.asText());
            });
        }
        return warnings;
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

    private JsonNode toCanonicalCamelNode(JsonNode node) {
        return toCanonicalCamelNode(node, false);
    }

    private JsonNode toCanonicalCamelNode(JsonNode node, boolean preserveKeysInObject) {
        if (node == null || node.isNull()) return node;

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, toCanonicalCamelNode(arr.get(i), preserveKeysInObject));
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
            String canonicalKey = preserveKeysInObject ? key : snakeToCamel(key);

            boolean preserveChildKeys = preserveKeysInObject || INDICATORS_KEY.equals(canonicalKey);
            dst.set(canonicalKey, toCanonicalCamelNode(src.get(key), preserveChildKeys));
        }

        return dst;
    }

    private String snakeToCamel(String key) {
        if (key == null || key.indexOf('_') < 0) return key;

        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
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
