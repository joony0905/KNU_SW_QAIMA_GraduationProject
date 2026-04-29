package com.qaima.external.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import com.qaima.dto.peercluster.PeerClusterDto;
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
    private Integer requestedPeerCount;
    private Integer effectivePeerCount;
    private Integer rawCandidateCount;
    private Integer evaluatedCandidateCount;
    private Integer eligibleCandidateCount;
    private Integer selectedPeerCount;
    private Integer displayedCandidateCount;
    private Integer displayLimit;
    private PeerClusterDto.AdjustmentMethod adjustmentMethod;
    private String industryIndexCode;
    private String industryIndexName;
    private Integer adjustedReturnSampleSize;
    private Double adjustedReturnCoverageRatio;
    private Boolean adjustmentValid;
    private String adjustmentFallbackReason;
    private String anchorStockCode;
    private List<PeerClusterInboundRelativePointDto> anchorSeries;
    private List<PeerClusterInboundRelativePointDto> industryIndexSeries;
    private List<PeerClusterInboundRelativePointDto> centroid;
    private List<PeerClusterInboundBandPointDto> band;
    private List<PeerClusterInboundRelativePointDto> peerCentroid;
    private List<PeerClusterInboundBandPointDto> peerBand;
    private List<PeerClusterInboundRelativePointDto> peerCoverage;
    private List<PeerClusterInboundPeerItemDto> peers;
    private List<PeerClusterInboundPeerItemDto> candidates;
    private OffsetDateTime asOf;
    private String interpretationNote;
    private List<String> warnings;
}
