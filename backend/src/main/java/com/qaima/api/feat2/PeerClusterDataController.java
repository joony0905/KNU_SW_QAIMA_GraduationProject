package com.qaima.api.feat2;

import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.service.feature2.PeerClusterDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/feature2/peercluster")
@RequiredArgsConstructor
@Slf4j
public class PeerClusterDataController {

    private final PeerClusterDataService peerClusterDataService;

    @PostMapping("/data")
    public Mono<Map<String, Object>> data(@RequestBody PeerClusterRequestDto req) {
        //FastAPI가 r.json()에서 바로 members/metas를 꺼내므로 ApiResponse로 감싸지않기
        return peerClusterDataService.buildPack(req)
                .doOnError(e -> log.error("[PeerClusterDataController] buildPack failed req={}", req, e));
    }
}