package com.qaima.api.feat2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class Feature2AnalyzeControllerRequestCanonicalizationTest {

    @Test
    void toCanonicalRequest_mapsSnakeCaseRequestToCamelDto() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Feature2AnalyzeController controller = new Feature2AnalyzeController(null, objectMapper);

        JsonNode raw = objectMapper.readTree("""
                {
                  "stock_code": "005930",
                  "window": 120,
                  "peer_count": 10,
                  "max_lag": 7,
                  "freq": "ONE_D"
                }
                """);

        Feature2AnalyzeRequestDto dto = invokeToCanonicalRequest(controller, raw);

        assertNotNull(dto);
        assertEquals("005930", dto.getStockCode());
        assertEquals(120, dto.getWindow());
        assertEquals(10, dto.getPeerCount());
        assertEquals(7, dto.getMaxLag());
        assertEquals("ONE_D", dto.getFreq().name());
    }

    private Feature2AnalyzeRequestDto invokeToCanonicalRequest(Feature2AnalyzeController controller, JsonNode raw) throws Exception {
        Method method = Feature2AnalyzeController.class.getDeclaredMethod("toCanonicalRequest", JsonNode.class);
        method.setAccessible(true);
        return (Feature2AnalyzeRequestDto) method.invoke(controller, raw);
    }
}
