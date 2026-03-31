package com.qaima.external.dto.peercluster;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PeerClusterInboundBandPointDto {
    private OffsetDateTime t;
    private Double p20;
    private Double p80;
}
