package com.qaima.service.feature2.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.Freq;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.industry.PeerClusterDto;
import com.qaima.repository.PeerClusterCacheRepository;
import com.qaima.service.feature2.PeerClusterResult;
import com.qaima.service.feature2.PeerClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    // DB fallback 템플릿
    private final PeerClusterCacheRepository peerClusterCacheRepository;

    @Override
    public Mono<PeerClusterResult> getPeerCluster(Long industryId, Freq freq, int window) {
        if (industryId == null) {
            return Mono.just(PeerClusterResult.emptyWithWarning(Feat2WarningCode.PEER_CLUSTER_MISSING.name()));
        }

        final String key = cacheKey(industryId, freq, window);

        // 1) Redis hit
        return redis.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        PeerClusterDto dto = objectMapper.readValue(json, PeerClusterDto.class);
                        return Mono.just(PeerClusterResult.builder().peerCluster(dto).build());
                    } catch (Exception e) {
                        log.warn("[PeerCluster] Redis JSON parse failed. key={}", key, e);
                        // JSON 파싱 실패면 fallback 시도
                        return fromDbFallback(industryId, freq, window)
                                .map(r -> r.addWarning(Feat2WarningCode.PEER_CLUSTER_JSON_PARSE_FAILED.name()));
                    }
                })
                .switchIfEmpty(
                        // 2) Redis miss -> DB fallback
                        fromDbFallback(industryId, freq, window)
                )
                .onErrorResume(e -> {
                    log.warn("[PeerCluster] cache read failed. industryId={}", industryId, e);
                    // throw 금지: 경고만 추가하고 null 반환
                    return Mono.just(PeerClusterResult.emptyWithWarning(Feat2WarningCode.PEER_CLUSTER_CACHE_READ_FAILED.name()));
                });
    }

    private Mono<PeerClusterResult> fromDbFallback(Long industryId, Freq freq, int window) {
        // MVP에서는 "최신 1건"만 가져오고, freq/window는 paramsJson로만 확인(또는 무시)하는 템플릿
        return Mono.fromCallable(() ->
                        peerClusterCacheRepository.findTopByIdIndustryIdAndIdMethodOrderByIdTsDesc(industryId, METHOD)
                                .orElse(null)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(row -> {
                    if (row == null) {
                        return Mono.just(PeerClusterResult.emptyWithWarning(Feat2WarningCode.PEER_CLUSTER_DB_EMPTY.name()));
                    }

                    final String json = row.getClusterJson();
                    try {
                        PeerClusterDto dto = objectMapper.readValue(json, PeerClusterDto.class);

                        // DB hit -> Redis write-through (best-effort)
                        return writeRedisBestEffort(cacheKey(industryId, freq, window), dto)
                                .thenReturn(PeerClusterResult.builder()
                                        .peerCluster(dto)
                                        .build());
                    } catch (Exception e) {
                        log.warn("[PeerCluster] DB JSON parse failed. industryId={}", industryId, e);
                        return Mono.just(PeerClusterResult.emptyWithWarning(Feat2WarningCode.PEER_CLUSTER_JSON_PARSE_FAILED.name()));
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[PeerCluster] DB read failed. industryId={}", industryId, e);
                    return Mono.just(PeerClusterResult.emptyWithWarning(Feat2WarningCode.PEER_CLUSTER_DB_READ_FAILED.name()));
                });
    }

    private Mono<Void> writeRedisBestEffort(String key, PeerClusterDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            return redis.opsForValue()
                    .set(key, json, TTL)
                    .then()
                    .onErrorResume(e -> {
                        log.warn("[PeerCluster] Redis write failed. key={}", key, e);
                        return Mono.empty();
                    });
        } catch (JsonProcessingException e) {
            log.warn("[PeerCluster] Redis serialize failed. key={}", key, e);
            return Mono.empty();
        }
    }

    private String cacheKey(Long industryId, Freq freq, int window) {
        // key에 version/method/freq/window 포함 (스키마 변경 대비)
        return "peer_cluster_cache:" + VERSION +
                ":" + METHOD +
                ":industryId=" + industryId +
                ":freq=" + (freq == null ? "D" : freq.name()) +
                ":window=" + window;
    }
}