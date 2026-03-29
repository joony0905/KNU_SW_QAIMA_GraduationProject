// backend/src/main/java/com/qaima/dto/peercluster/PeerItemDto.java
package com.qaima.dto.peercluster;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class PeerItemDto {

    @JsonAlias("stock_code")
    private String stockCode;

    @JsonAlias("company_name")
    private String companyName;

    // liquidity (optional)
    @JsonAlias("avg_turnover")
    private Double avgTurnover;

    @JsonAlias("avg_volume")
    private Double avgVolume;

    // correlation
    private Double corr;            // same-time correlation

    @JsonAlias("best_lag")
    private Integer bestLag;        // lag (day)

    @JsonAlias("lead_lag_corr")
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
