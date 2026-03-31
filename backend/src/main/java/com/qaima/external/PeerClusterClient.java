package com.qaima.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qaima.dto.peercluster.BandPointDto;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerItemDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
import com.qaima.dto.peercluster.RelativePointDto;
import com.qaima.external.dto.peercluster.PeerClusterInboundBandPointDto;
import com.qaima.external.dto.peercluster.PeerClusterInboundPeerItemDto;
import com.qaima.external.dto.peercluster.PeerClusterInboundRelativePointDto;
import com.qaima.external.dto.peercluster.PeerClusterInboundResponseDto;
import java.util.List;
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
                        PeerClusterResponseDto parsed =
                                snakeCaseResponseValue(extractPayload(root, "feature2/peer-cluster"));
                        log.info("[PeerClusterClient] parsed={}", parsed);
                        return parsed;
                    } catch (Exception e) {
                        log.error("[PeerClusterClient] JSON parse failed. body={}", body, e);
                        throw new RuntimeException("JSON parse failed", e);
                    }
                });
    }

    private PeerClusterResponseDto snakeCaseResponseValue(JsonNode payloadNode) throws com.fasterxml.jackson.core.JsonProcessingException {
        PeerClusterInboundResponseDto inbound = objectMapper.treeToValue(payloadNode, PeerClusterInboundResponseDto.class);
        return toResponseDto(inbound);
    }

    private PeerClusterResponseDto toResponseDto(PeerClusterInboundResponseDto inbound) {
        PeerClusterResponseDto response = new PeerClusterResponseDto();
        response.setMethod(inbound.getMethod());
        response.setIndustryId(inbound.getIndustryId());
        response.setFreq(inbound.getFreq());
        response.setWindow(inbound.getWindow());
        response.setPeerCount(inbound.getPeerCount());
        response.setAnchorStockCode(inbound.getAnchorStockCode());
        response.setCentroid(mapCentroid(inbound.getCentroid()));
        response.setBand(mapBand(inbound.getBand()));
        response.setPeers(mapPeers(inbound.getPeers()));
        response.setAsOf(inbound.getAsOf());
        response.setWarnings(inbound.getWarnings());
        return response;
    }

    private List<RelativePointDto> mapCentroid(List<PeerClusterInboundRelativePointDto> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .map(point -> RelativePointDto.builder()
                        .ts(point.getT())
                        .pct(point.getValue())
                        .build())
                .toList();
    }

    private List<BandPointDto> mapBand(List<PeerClusterInboundBandPointDto> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .map(point -> BandPointDto.builder()
                        .ts(point.getT())
                        .p20(point.getP20())
                        .p80(point.getP80())
                        .build())
                .toList();
    }

    private List<PeerItemDto> mapPeers(List<PeerClusterInboundPeerItemDto> peers) {
        if (peers == null) {
            return List.of();
        }
        return peers.stream()
                .map(this::toPeerItemDto)
                .toList();
    }

    private PeerItemDto toPeerItemDto(PeerClusterInboundPeerItemDto peer) {
        return PeerItemDto.builder()
                .stockCode(peer.getStockCode())
                .companyName(peer.getCompanyName())
                .avgTurnover(peer.getAvgTurnover())
                .avgVolume(peer.getAvgVolume())
                .corr(peer.getCorr())
                .bestLag(peer.getBestLag())
                .leadLagCorr(peer.getLeadLagCorr())
                .relation(peer.getRelation())
                .score(peer.getScore())
                .build();
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
