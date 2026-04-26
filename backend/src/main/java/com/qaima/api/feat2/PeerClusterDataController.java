package com.qaima.api.feat2;

import com.qaima.domain.Freq;
import com.qaima.dto.peercluster.PeerClusterRequestDto;
import com.qaima.service.feature2.PeerClusterDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/feature2/peercluster")
@RequiredArgsConstructor
@Slf4j
public class PeerClusterDataController {

    private final PeerClusterDataService peerClusterDataService;

    @PostMapping("/data")
    public Mono<Map<String, Object>> data(@RequestBody Map<String, Object> body) {
        //FastAPI가 r.json()에서 바로 members/metas를 꺼내므로 ApiResponse로 감싸지않기
        PeerClusterRequestDto req = normalize(body);
        log.info("[PeerClusterDataController] normalized req={}", req);
        return peerClusterDataService.buildPack(req)
                .doOnError(e -> log.error("[PeerClusterDataController] buildPack failed req={}", req, e));
    }

    private PeerClusterRequestDto normalize(Map<String, Object> body) {
        if (body == null) {
            return null;
        }

        return PeerClusterRequestDto.builder()
                .industryId(asLong(first(body, "industry_id", "industryId")))
                .anchorStockCode(asString(first(body, "anchor_stock_code", "anchorStockCode")))
                .freq(asFreq(first(body, "freq")))
                .window(asInteger(first(body, "window")))
                .peerCount(asInteger(first(body, "peer_count", "peerCount")))
                .maxLag(asInteger(first(body, "max_lag", "maxLag")))
                .displayLimit(asInteger(first(body, "display_limit", "displayLimit")))
                .build();
    }

    private Object first(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (body.containsKey(key)) {
                return body.get(key);
            }
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Freq asFreq(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Freq.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
