package com.qaima.external.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PeerClusterInboundResponseDto {
    private String method;
    private Long industryId;
    private Freq freq;
    private Integer window;
    private Integer peerCount;
    private String anchorStockCode;
    private List<PeerClusterInboundRelativePointDto> centroid;
    private List<PeerClusterInboundBandPointDto> band;
    private List<PeerClusterInboundPeerItemDto> peers;
    private OffsetDateTime asOf;
    private List<String> warnings;
}
