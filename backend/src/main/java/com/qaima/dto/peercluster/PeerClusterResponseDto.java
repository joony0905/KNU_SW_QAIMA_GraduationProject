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
    private String anchorStockCode;
    private List<RelativePointDto> centroid;
    private List<BandPointDto> band;
    private List<PeerItemDto> peers;
    private OffsetDateTime asOf;
    private List<String> warnings;
}