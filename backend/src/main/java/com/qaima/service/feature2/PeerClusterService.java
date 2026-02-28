package com.qaima.service.feature2;

import com.qaima.domain.Freq;
import reactor.core.publisher.Mono;

public interface PeerClusterService {
    Mono<PeerClusterResult> getPeerCluster(Long industryId, Freq freq, int window);
}