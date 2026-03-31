package com.qaima.service.feature2.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.dto.peercluster.PeerClusterResponseDto;
import com.qaima.external.PeerClusterClient;
import com.qaima.service.feature2.PeerClusterResult;
import com.qaima.service.feature2.PeerClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeerClusterServiceImpl implements PeerClusterService {

    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final PeerClusterClient peerClusterClient;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<PeerClusterResult> getPeerCluster(
            Long industryId,
            String anchorStockCode,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        if (industryId == null) {
            return Mono.just(PeerClusterResult.empty(
                    Feat2WarningCode.PEER_CLUSTER_INDUSTRY_ID_MISSING.name()
            ));
        }

        if (anchorStockCode == null || anchorStockCode.isBlank()) {
            return Mono.just(PeerClusterResult.empty(
                    Feat2WarningCode.PEER_CLUSTER_ANCHOR_MISSING.name()
            ));
        }

        String cacheKey = buildCacheKey(industryId, anchorStockCode, freq, window, peerCount, maxLag);

        return getFromCache(cacheKey)
                .onErrorResume(e -> {
                    log.warn("[PeerCluster] cache read failed. fallback to FastAPI. key={}", cacheKey, e);
                    return Mono.empty();
                })
                .flatMap(cached -> {
                    log.info("[PeerCluster] cache hit key={}", cacheKey);
                    return Mono.just(
                            PeerClusterResult.builder()
                                    .peerCluster(cached)
                                    .warnings(new ArrayList<>())
                                    .build()
                    );
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[PeerCluster] cache miss key={}", cacheKey);

                    PeerClusterRequestDto req = PeerClusterRequestDto.builder()
                            .industryId(industryId)
                            .anchorStockCode(anchorStockCode)
                            .freq(freq)
                            .window(window)
                            .peerCount(peerCount)
                            .maxLag(maxLag)
                            .build();

                    return peerClusterClient.requestPeerCluster(req)
                            .doOnNext(res -> log.info("[PeerCluster] FastAPI parsed response={}", res))
                            .map(this::toResult)
                            .flatMap(result -> {
                                PeerClusterDto dto = result.getPeerCluster();
                                if (dto == null) {
                                    return Mono.just(result.addWarning(
                                            Feat2WarningCode.PEER_CLUSTER_MISSING.name()
                                    ));
                                }

                                return putToCache(cacheKey, dto)
                                        .onErrorResume(e -> {
                                            log.warn("[PeerCluster] cache write failed. continue without cache. key={}", cacheKey, e);
                                            return Mono.just(false);
                                        })
                                        .thenReturn(result);
                            })
                            .onErrorResume(e -> {
                                log.error("[PeerCluster] fail req={}", req, e);
                                return Mono.just(
                                        PeerClusterResult.empty(
                                                Feat2WarningCode.PEER_CLUSTER_INTERNAL_ERROR.name()
                                        )
                                );
                            });
                }));
    }

    private String buildCacheKey(
            Long industryId,
            String anchorStockCode,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        return String.format(
                "feature2:peercluster:v2:%d:%s:%s:%d:%d:%d",
                industryId,
                anchorStockCode,
                freq.name(),
                window,
                peerCount,
                maxLag
        );
    }

    private Mono<PeerClusterDto> getFromCache(String key) {
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        PeerClusterDto dto = objectMapper.readValue(json, PeerClusterDto.class);
                        if (!isUsableCache(dto)) {
                            log.warn("[PeerCluster] cache payload invalid. fallback to FastAPI. key={}", key);
                            return Mono.empty();
                        }
                        return Mono.just(dto);
                    } catch (Exception e) {
                        log.warn("[PeerCluster] cache deserialize failed key={}", key, e);
                        return Mono.empty();
                    }
                });
    }

    private Mono<Boolean> putToCache(String key, PeerClusterDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            return redisTemplate.opsForValue()
                    .set(key, json, CACHE_TTL)
                    .doOnNext(saved ->
                            log.info("[PeerCluster] cache saved key={}, saved={}", key, saved)
                    );
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private PeerClusterResult toResult(PeerClusterResponseDto resp) {
        List<String> warnings = resp.getWarnings() != null
                ? new ArrayList<>(resp.getWarnings())
                : new ArrayList<>();

        PeerClusterDto dto = PeerClusterDto.builder()
                .method(resp.getMethod())
                .industryId(resp.getIndustryId())
                .freq(resp.getFreq())
                .window(resp.getWindow())
                .peerCount(resp.getPeerCount())
                .anchorStockCode(resp.getAnchorStockCode())
                .centroid(resp.getCentroid())
                .band(resp.getBand())
                .peers(resp.getPeers())
                .asOf(resp.getAsOf())
                .build();

        return PeerClusterResult.builder()
                .peerCluster(dto)
                .warnings(warnings)
                .build();
    }

    private boolean isUsableCache(PeerClusterDto dto) {
        if (dto == null) {
            return false;
        }
        return dto.getIndustryId() != null
                && dto.getPeerCount() != null
                && dto.getAnchorStockCode() != null
                && !dto.getAnchorStockCode().isBlank()
                && dto.getMethod() != null
                && dto.getFreq() != null
                && dto.getWindow() != null;
    }
}
