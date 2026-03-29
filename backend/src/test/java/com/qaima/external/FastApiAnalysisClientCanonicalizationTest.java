package com.qaima.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.Freq;
import com.qaima.dto.featone.FeatOneRequestDto;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FastApiAnalysisClientCanonicalizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FastApiAnalysisClient client;

    @BeforeEach
    void setUp() {
        client = new FastApiAnalysisClient(null, objectMapper);
    }

    @Test
    void toCanonicalCamelNode_preservesIndicatorIdentifierKeys_andCanonicalizesGeneralFields() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "stock_code": "005930",
                  "ohlcv_summary": {},
                  "indicators": {
                    "bb20_2": [],
                    "stoch14_3_3": [],
                    "ema": {
                      "20": []
                    }
                  }
                }
                """);

        JsonNode output = invokeCanonicalize(input);

        assertEquals("005930", output.path("stockCode").asText());
        assertTrue(output.has("ohlcvSummary"));
        assertTrue(output.has("indicators"));

        JsonNode indicators = output.path("indicators");
        assertTrue(indicators.has("bb20_2"));
        assertTrue(indicators.has("stoch14_3_3"));
        assertTrue(indicators.has("ema"));
        assertTrue(indicators.path("ema").has("20"));

        assertTrue(indicators.path("bb20_2").isArray());
        assertTrue(indicators.path("stoch14_3_3").isArray());
    }

    @Test
    void toCanonicalCamelNode_wouldBreakIndicatorKeysWithoutIndicatorBoundaryProtection() throws Exception {
        assertEquals("bb202", invokeSnakeToCamel("bb20_2"));
        assertEquals("stoch1433", invokeSnakeToCamel("stoch14_3_3"));
    }

    @Test
    void requirePayloadOnlyRoot_rejectsLegacyEnvelope() throws Exception {
        JsonNode envelope = objectMapper.readTree("""
                {
                  "data": {
                    "metrics": {}
                  }
                }
                """);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeRequirePayloadOnlyRootUnchecked(envelope));
        assertTrue(ex.getMessage().contains("payload-only"));
    }

    @Test
    void readWarnings_readsOnlyPayloadWarnings() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "warnings": ["A", "B"],
                  "meta": { "warnings": ["LEGACY"] }
                }
                """);

        @SuppressWarnings("unchecked")
        java.util.List<String> warnings = (java.util.List<String>) invokeReadWarnings(payload);
        assertEquals(java.util.List.of("A", "B"), warnings);
    }

    @Test
    void toSnakeCaseNode_matchesFeature1FastApiRequestShape_withoutExtraOptionsField() throws Exception {
        FeatOneRequestDto req = FeatOneRequestDto.builder()
                .stockCode("005930")
                .freq(Freq.ONE_D)
                .ohlcv(List.of())
                .financials(List.of())
                .includeExplain(false)
                .build();

        JsonNode snake = invokeToSnakeCase(req);
        assertTrue(snake.has("stock_code"));
        assertTrue(snake.has("freq"));
        assertTrue(snake.has("ohlcv"));
        assertTrue(snake.has("financials"));
        assertTrue(snake.has("include_explain"));
        assertTrue(!snake.has("options"));
    }

    private JsonNode invokeCanonicalize(JsonNode input) throws Exception {
        Method method = FastApiAnalysisClient.class.getDeclaredMethod("toCanonicalCamelNode", JsonNode.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(client, input);
    }

    private String invokeSnakeToCamel(String key) throws Exception {
        Method method = FastApiAnalysisClient.class.getDeclaredMethod("snakeToCamel", String.class);
        method.setAccessible(true);
        return (String) method.invoke(client, key);
    }

    private Object invokeReadWarnings(JsonNode payload) throws Exception {
        Method method = FastApiAnalysisClient.class.getDeclaredMethod("readWarnings", JsonNode.class);
        method.setAccessible(true);
        return method.invoke(client, payload);
    }

    private JsonNode invokeRequirePayloadOnlyRoot(JsonNode root) throws Exception {
        Method method = FastApiAnalysisClient.class.getDeclaredMethod("requirePayloadOnlyRoot", JsonNode.class, String.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(client, root, "feature1");
    }

    private JsonNode invokeToSnakeCase(FeatOneRequestDto requestDto) throws Exception {
        Method method = FastApiAnalysisClient.class.getDeclaredMethod("toSnakeCaseNode", Object.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(client, requestDto);
    }

    private JsonNode invokeRequirePayloadOnlyRootUnchecked(JsonNode root) {
        try {
            return invokeRequirePayloadOnlyRoot(root);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException stateEx) {
                throw stateEx;
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
