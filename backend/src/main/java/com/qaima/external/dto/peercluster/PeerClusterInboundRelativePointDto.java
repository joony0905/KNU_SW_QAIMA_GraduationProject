package com.qaima.external.dto.peercluster;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeerClusterInboundRelativePointDto {
    private OffsetDateTime t;
    private Double value;
}
