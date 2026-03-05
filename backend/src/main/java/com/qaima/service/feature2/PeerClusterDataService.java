package com.qaima.service.feature2;

import com.qaima.dto.peercluster.PeerClusterRequestDto;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface PeerClusterDataService {
    Mono<Map<String, Object>> buildPack(PeerClusterRequestDto req);
}