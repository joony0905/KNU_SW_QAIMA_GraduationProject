package com.qaima.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
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
}
