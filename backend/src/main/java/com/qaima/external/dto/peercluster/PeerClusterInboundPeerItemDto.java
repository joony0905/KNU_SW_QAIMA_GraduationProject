package com.qaima.external.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.peercluster.PeerItemDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PeerClusterInboundPeerItemDto {
    private String stockCode;
    private String companyName;
    private Double avgTurnover;
    private Double avgVolume;
    private Double corr;
    private Integer bestLag;
    private Double leadLagCorr;
    private PeerItemDto.Relation relation;
    private Double score;
}
