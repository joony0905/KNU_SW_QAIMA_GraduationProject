// backend/src/main/java/com/qaima/dto/peercluster/PeerItemDto.java
package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class PeerItemDto {

    private String stockCode;
    private String companyName;

    // liquidity (optional)
    private Double avgTurnover;
    private Double avgVolume;

    // correlation
    private Double corr;            // same-time correlation
    private Integer bestLag;        // lag (day)
    private Double leadLagCorr;     // corr at bestLag

    // relation 판단
    private Relation relation;      // LEADER / FOLLOWER / COINCIDENT / UNKNOWN

    // ranking score (MVP: 0.7*corr + 0.3*leadLagCorr)
    private Double score;

    public enum Relation {
        LEADER,
        FOLLOWER,
        COINCIDENT,
        UNKNOWN
    }
}