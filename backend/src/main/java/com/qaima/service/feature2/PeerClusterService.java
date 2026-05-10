// backend/src/main/java/com/qaima/service/feature2/PeerClusterService.java
package com.qaima.service.feature2;

import com.qaima.domain.Freq;
import java.time.OffsetDateTime;
import reactor.core.publisher.Mono;

/**
 * PeerCluster 제공의 단일 진입점
 *
 * 정책:
 * - throw 금지
 * - 실패/부분성공은 warnings
 * - peerCluster는 null 가능
 */
public interface PeerClusterService {

    Mono<PeerClusterResult> getPeerCluster(
            Long industryId,
            String anchorStockCode,
            Freq freq,
            int window,
            OffsetDateTime from,
            OffsetDateTime to,
            int peerCount,
            int maxLag,
            int displayLimit
    );
}
