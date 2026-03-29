package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.feature2.Feature2AnalyzeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/feature2")
public class Feature2AnalyzeController {

    private final Feature2AnalyzeService feature2AnalyzeService;
    private final ObjectMapper objectMapper;

    @PostMapping("/analyze")
    public Mono<ApiResponse<Feature2AnalyzeResponseDto>> analyze(
            @RequestBody(required = false) JsonNode reqRaw
    ) {
        Feature2AnalyzeRequestDto req = toCanonicalRequest(reqRaw);
        log.info("[Feature2AnalyzeController][request] raw={}", reqRaw);
        log.info("[Feature2AnalyzeController][request] stockCode={}, freq={}, window={}",
                req != null ? req.getStockCode() : null,
                req != null ? req.getFreq() : null,
                req != null ? req.getWindow() : null);

        return feature2AnalyzeService.analyze(req)
                .map(this::wrapWithWarnings)
                .onErrorResume(ex -> {
                    log.error("[Feature2] unexpected error in controller. cause={}", ex.getMessage(), ex);

                    Feature2AnalyzeResponseDto fallback = Feature2AnalyzeResponseDto.builder()
                            .metrics(Feature2MetricsDto.empty())
                            .explain(null)
                            .warnings(List.of(Feat2WarningCode.FEAT2_INTERNAL_ERROR.name()))
                            .build();

                    return Mono.just(wrapWithWarnings(fallback));
                });
    }

    private ApiResponse<Feature2AnalyzeResponseDto> wrapWithWarnings(Feature2AnalyzeResponseDto res) {
        List<String> warnings = (res == null || res.getWarnings() == null) ? List.of() : res.getWarnings();
        return ApiResponse.successWithWarnings(res, warnings);
    }

    private Feature2AnalyzeRequestDto toCanonicalRequest(JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return null;
        }
        JsonNode canonical = toCanonicalCamelNode(raw.deepCopy());
        return objectMapper.convertValue(canonical, Feature2AnalyzeRequestDto.class);
    }

    private JsonNode toCanonicalCamelNode(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, toCanonicalCamelNode(node.get(i)));
            }
            return node;
        }
        if (!node.isObject()) {
            return node;
        }

        com.fasterxml.jackson.databind.node.ObjectNode src = (com.fasterxml.jackson.databind.node.ObjectNode) node;
        com.fasterxml.jackson.databind.node.ObjectNode dst = objectMapper.createObjectNode();
        java.util.Iterator<String> fieldNames = src.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            dst.set(snakeToCamel(key), toCanonicalCamelNode(src.get(key)));
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
}
