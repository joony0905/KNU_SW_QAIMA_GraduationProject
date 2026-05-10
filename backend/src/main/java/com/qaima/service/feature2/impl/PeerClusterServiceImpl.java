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
import java.time.OffsetDateTime;
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
            OffsetDateTime from,
            OffsetDateTime to,
            int peerCount,
            int maxLag,
            int displayLimit
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

        String cacheKey = buildCacheKey(industryId, anchorStockCode, freq, window, from, to, peerCount, maxLag, displayLimit);

        return getFromCache(cacheKey)
                .onErrorResume(e -> {
                    log.warn("[PeerCluster] cache read failed. fallback to FastAPI. key={}", cacheKey, e);
                    return Mono.empty();
                })
                .flatMap(cached -> {
                    log.info("[PeerCluster] cache hit key={}", cacheKey);
                    return Mono.just(cached);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[PeerCluster] cache miss key={}", cacheKey);

                    PeerClusterRequestDto req = PeerClusterRequestDto.builder()
                            .industryId(industryId)
                            .anchorStockCode(anchorStockCode)
                            .freq(freq)
                            .window(window)
                            .from(from)
                            .to(to)
                            .peerCount(peerCount)
                            .maxLag(maxLag)
                            .displayLimit(displayLimit)
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

                                return putToCache(cacheKey, result)
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
            OffsetDateTime from,
            OffsetDateTime to,
            int peerCount,
            int maxLag,
            int displayLimit
    ) {
        return String.format(
                "feature2:peercluster:v6:%d:%s:%s:%d:%s:%s:%d:%d:%d",
                industryId,
                anchorStockCode,
                freq.name(),
                window,
                from == null ? "-" : from.toInstant().toString(),
                to == null ? "-" : to.toInstant().toString(),
                peerCount,
                maxLag,
                displayLimit
        );
    }

    private Mono<PeerClusterResult> getFromCache(String key) {
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(json -> {
                    try {
                        CachedPeerCluster cached = objectMapper.readValue(json, CachedPeerCluster.class);
                        if (isUsableCache(cached.getPeerCluster())) {
                            return Mono.just(PeerClusterResult.builder()
                                    .peerCluster(cached.getPeerCluster())
                                    .warnings(cached.getWarnings() != null ? cached.getWarnings() : new ArrayList<>())
                                    .build());
                        }
                        return readLegacyCache(json, key);
                    } catch (Exception e) {
                        return readLegacyCache(json, key);
                    }
                });
    }

    private Mono<PeerClusterResult> readLegacyCache(String json, String key) {
        try {
            PeerClusterDto legacyDto = objectMapper.readValue(json, PeerClusterDto.class);
            if (!isUsableCache(legacyDto)) {
                log.warn("[PeerCluster] cache payload invalid. fallback to FastAPI. key={}", key);
                return Mono.empty();
            }
            return Mono.just(PeerClusterResult.builder()
                    .peerCluster(legacyDto)
                    .warnings(new ArrayList<>())
                    .build());
        } catch (Exception legacyError) {
            log.warn("[PeerCluster] cache deserialize failed key={}", key, legacyError);
            return Mono.empty();
        }
    }

    private Mono<Boolean> putToCache(String key, PeerClusterResult result) {
        try {
            CachedPeerCluster cached = new CachedPeerCluster(
                    result.getPeerCluster(),
                    result.getWarnings() != null ? result.getWarnings() : new ArrayList<>()
            );
            String json = objectMapper.writeValueAsString(cached);
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
                .requestedPeerCount(resp.getRequestedPeerCount())
                .effectivePeerCount(resp.getEffectivePeerCount())
                .rawCandidateCount(resp.getRawCandidateCount())
                .evaluatedCandidateCount(resp.getEvaluatedCandidateCount())
                .eligibleCandidateCount(resp.getEligibleCandidateCount())
                .selectedPeerCount(resp.getSelectedPeerCount())
                .displayedCandidateCount(resp.getDisplayedCandidateCount())
                .displayLimit(resp.getDisplayLimit())
                .adjustmentMethod(resp.getAdjustmentMethod())
                .industryIndexCode(resp.getIndustryIndexCode())
                .industryIndexName(resp.getIndustryIndexName())
                .adjustedReturnSampleSize(resp.getAdjustedReturnSampleSize())
                .adjustedReturnCoverageRatio(resp.getAdjustedReturnCoverageRatio())
                .adjustmentValid(resp.getAdjustmentValid())
                .adjustmentFallbackReason(resp.getAdjustmentFallbackReason())
                .anchorStockCode(resp.getAnchorStockCode())
                .anchorSeries(resp.getAnchorSeries())
                .industryIndexSeries(resp.getIndustryIndexSeries())
                .centroid(resp.getCentroid())
                .band(resp.getBand())
                .peerCentroid(resp.getPeerCentroid())
                .peerBand(resp.getPeerBand())
                .peerCoverage(resp.getPeerCoverage())
                .peers(resp.getPeers())
                .candidates(resp.getCandidates())
                .asOf(resp.getAsOf())
                .interpretationNote(resp.getInterpretationNote())
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

    private static class CachedPeerCluster {
        private PeerClusterDto peerCluster;
        private List<String> warnings;

        public CachedPeerCluster() {
        }

        public CachedPeerCluster(PeerClusterDto peerCluster, List<String> warnings) {
            this.peerCluster = peerCluster;
            this.warnings = warnings;
        }

        public PeerClusterDto getPeerCluster() {
            return peerCluster;
        }

        public void setPeerCluster(PeerClusterDto peerCluster) {
            this.peerCluster = peerCluster;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }
}
