package com.qaima.external;

import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class PeerClusterClient {

    @Qualifier("analysisWebClient")
    private final WebClient webClient;

    public PeerClusterClient(@Qualifier("analysisWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<PeerClusterResponseDto> requestPeerCluster(PeerClusterRequestDto req) {
        return webClient.post()
                .uri("/feature2/peer-cluster")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(PeerClusterResponseDto.class)
                .onErrorResume(e -> {
                    log.warn("[FastAPI] peer-cluster call failed", e);
                    return Mono.just(new PeerClusterResponseDto());
                });
    }
}
