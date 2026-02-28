package com.qaima.service.feature2;

import com.qaima.dto.industry.PeerClusterDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PeerClusterResult {
    private PeerClusterDto peerCluster; // nullable
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    public static PeerClusterResult emptyWithWarning(String warningCodeName) {
        return PeerClusterResult.builder()
                .peerCluster(null)
                .warnings(new ArrayList<>(List.of(warningCodeName)))
                .build();
    }

    public PeerClusterResult addWarning(String warningCodeName) {
        this.warnings.add(warningCodeName);
        return this;
    }
}