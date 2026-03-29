package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
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
                .bodyValue(req)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(body -> log.info("[FastAPI RAW] {}", body))
                .map(body -> {
                    try {
                        ObjectMapper snakeMapper = objectMapper.copy()
                                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                        PeerClusterResponseDto parsed =
                                snakeMapper.readValue(body, PeerClusterResponseDto.class);
                        log.info("[PeerClusterClient] parsed={}", parsed);
                        return parsed;
                    } catch (Exception e) {
                        log.error("[PeerClusterClient] JSON parse failed. body={}", body, e);
                        throw new RuntimeException("JSON parse failed", e);
                    }
                });
    }
}