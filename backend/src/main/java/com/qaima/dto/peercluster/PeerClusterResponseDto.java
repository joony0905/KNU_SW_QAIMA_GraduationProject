package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
public class PeerClusterResponseDto {
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
    private List<RelativePointDto> anchorSeries;
    private List<RelativePointDto> industryIndexSeries;
    private List<RelativePointDto> centroid;
    private List<BandPointDto> band;
    private List<RelativePointDto> peerCentroid;
    private List<BandPointDto> peerBand;
    private List<PeerItemDto> peers;
    private List<PeerItemDto> candidates;
    private OffsetDateTime asOf;
    private String interpretationNote;
    private List<String> warnings;
}
