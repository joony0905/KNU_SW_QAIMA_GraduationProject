// backend/src/main/java/com/qaima/service/feature2/impl/PeerClusterServiceImpl.java
package com.qaima.service.feature2.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.external.PeerClusterClient;
import com.qaima.service.feature2.PeerClusterResult;
import com.qaima.service.feature2.PeerClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeerClusterServiceImpl implements PeerClusterService {

    private static final String METHOD = "INDUSTRY_CORR_V1";
    private static final String VERSION = "v1";
    private static final Duration TTL = Duration.ofHours(12);

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final PeerClusterClient fastApiClient;

    @Override
    public Mono<PeerClusterResult> getPeerCluster(
            Long industryId,
            String anchorStockCode,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        if (industryId == null || anchorStockCode == null) {
            return Mono.just(PeerClusterResult.empty(
                    Feat2WarningCode.PEER_CLUSTER_MISSING.name()
            ));
        }

        final String key = cacheKey(industryId, freq, window, peerCount, maxLag);

        // 1) Redis hit
        return redis.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        PeerClusterDto dto = objectMapper.readValue(json, PeerClusterDto.class);
                        return Mono.just(PeerClusterResult.builder()
                                .peerCluster(dto)
                                .build());
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })

                // 2) Redis miss → FastAPI 계산
                .switchIfEmpty(
                        callFastApi(industryId, anchorStockCode, freq, window, peerCount, maxLag)
                                .flatMap(result -> {
                                    if (result.getPeerCluster() != null) {
                                        writeRedisBestEffort(key, result.getPeerCluster());
                                    }
                                    return Mono.just(result);
                                })
                )

                // 3) 최상위 보호
                .onErrorResume(e -> {
                    log.warn("[PeerCluster] unexpected failure", e);
                    return Mono.just(
                            PeerClusterResult.empty(
                                    Feat2WarningCode.PEER_CLUSTER_INTERNAL_ERROR.name()
                            )
                    );
                });
    }

    private Mono<PeerClusterResult> callFastApi(
            Long industryId,
            String anchorStockCode,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        PeerClusterRequestDto req = PeerClusterRequestDto.builder()
                .industryId(industryId)
                .anchorStockCode(anchorStockCode)
                .freq(freq)
                .window(window)
                .peerCount(peerCount)
                .maxLag(maxLag)
                .build();

        return fastApiClient.requestPeerCluster(req)
                .map(resp -> {
                    PeerClusterResult r = PeerClusterResult.builder()
                            .peerCluster(resp.getPeerCluster())
                            .build();

                    if (resp.getWarnings() != null) {
                        r.getWarnings().addAll(resp.getWarnings());
                    }
                    return r;
                })
                .onErrorResume(e ->
                        Mono.just(PeerClusterResult.empty(
                                Feat2WarningCode.PEER_CLUSTER_MISSING.name()
                        ))
                );
    }

    private void writeRedisBestEffort(String key, PeerClusterDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redis.opsForValue().set(key, json, TTL).subscribe();
        } catch (Exception e) {
            log.warn("[PeerCluster] redis write failed", e);
        }
    }

    private String cacheKey(Long industryId, Freq freq, int window, int peerCount, int maxLag) {
        return "peer_cluster:" + VERSION +
                ":" + METHOD +
                ":industry=" + industryId +
                ":freq=" + freq.name() +
                ":window=" + window +
                ":peers=" + peerCount +
                ":lag=" + maxLag;
    }
}