// backend/src/main/java/com/qaima/service/feature2/PeerClusterResult.java
package com.qaima.service.feature2;

import com.qaima.dto.peercluster.PeerClusterDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature2 내부 전용 결과 래퍼
 * (Controller / 외부 계약에는 노출되지 않음)
 */
@Data
@Builder
public class PeerClusterResult {

    private PeerClusterDto peerCluster; // nullable

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public static PeerClusterResult empty(String warning) {
        return PeerClusterResult.builder()
                .peerCluster(null)
                .warnings(new ArrayList<>(List.of(warning)))
                .build();
    }

    public PeerClusterResult addWarning(String warning) {
        this.warnings.add(warning);
        return this;
    }
}