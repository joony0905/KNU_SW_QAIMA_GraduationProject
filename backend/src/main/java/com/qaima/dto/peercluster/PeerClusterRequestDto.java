package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Data;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@Builder
public class PeerClusterRequestDto {

    private Long industryId;
    private String anchorStockCode;
    private Freq freq;
    private Integer window;
    private Integer peerCount;
    private Integer maxLag;
}