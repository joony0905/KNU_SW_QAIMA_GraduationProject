package com.qaima.external;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PeerClusterClientCanonicalizationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PeerClusterClient client;

    @BeforeEach
    void setUp() {
        client = new PeerClusterClient(null, objectMapper);
    }

    @Test
    void requirePayloadOnlyRoot_rejectsLegacyEnvelope() throws Exception {
        JsonNode envelope = objectMapper.readTree("""
                {
                  "data": {
                    "peer_count": 8
                  }
                }
                """);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokeRequirePayloadOnlyRootUnchecked(envelope));
        assertTrue(ex.getMessage().contains("payload-only"));
    }

    private JsonNode invokeRequirePayloadOnlyRoot(JsonNode root) throws Exception {
        Method method = PeerClusterClient.class.getDeclaredMethod("requirePayloadOnlyRoot", JsonNode.class, String.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(client, root, "feature2/peer-cluster");
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
