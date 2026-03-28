// backend/src/main/java/com/qaima/dto/peercluster/PeerClusterDto.java
package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class PeerClusterDto {

    private String method;          // "INDUSTRY_CORR_V1"
    private Long industryId;
    private Freq freq;              // ONE_D / ONE_W
    private Integer window;         // 90
    private Integer peerCount;
    private String anchorStockCode;
    // representative series
    private List<RelativePointDto> centroid;   // (ts, value)
    private List<BandPointDto> band;            // (ts, p20, p80) optional

    // peers (final contract)
    private List<PeerItemDto> peers;

    private OffsetDateTime asOf;    // calculation timestamp
}