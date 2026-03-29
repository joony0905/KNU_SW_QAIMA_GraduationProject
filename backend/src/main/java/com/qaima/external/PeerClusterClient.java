package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class PeerClusterClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public PeerClusterClient(
            @Qualifier("analysisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Mono<PeerClusterResponseDto> requestPeerCluster(PeerClusterRequestDto req) {
        return webClient.post()
                .uri("/feature2/peer-cluster")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(toSnakeCaseNode(req))
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("[FastAPI RAW] {}", body))
                .map(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode payloadNode = root.has("data") ? root.get("data") : root;
                        JsonNode canonicalPayload = toCanonicalCamelNode(payloadNode.deepCopy());

                        PeerClusterResponseDto parsed =
                                objectMapper.treeToValue(canonicalPayload, PeerClusterResponseDto.class);
                        log.info("[PeerClusterClient] parsed={}", parsed);
                        return parsed;
                    } catch (Exception e) {
                        log.error("[PeerClusterClient] JSON parse failed. body={}", body, e);
                        throw new RuntimeException("JSON parse failed", e);
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
            dst.set(camelToSnake(key), toSnakeCaseNodeRecursive(src.get(key)));
        }
        return dst;
    }

    private JsonNode toCanonicalCamelNode(JsonNode node) {
        if (node == null || node.isNull()) return node;

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, toCanonicalCamelNode(arr.get(i)));
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
