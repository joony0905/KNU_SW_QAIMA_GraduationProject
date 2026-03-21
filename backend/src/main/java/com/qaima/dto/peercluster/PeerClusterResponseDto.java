package com.qaima.dto.peercluster;
import lombok.Data;

import java.util.List;

@Data
public class PeerClusterResponseDto {

    private PeerClusterDto peerCluster; // nullable
    private List<String> warnings;
}
