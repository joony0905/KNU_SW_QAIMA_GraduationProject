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

    // 유동성 정보. 선택적으로 포함된다.
    private Double avgTurnover;
    private Double avgVolume;

    // 상관계수
    private Double corr;            // 동시점 상관계수
    private Double adjustedCorr;
    private Double corrStability;
    private Boolean rawCorrValid;
    private Boolean adjustedCorrValid;
    private Integer adjustedReturnSampleSize;
    private Double adjustedReturnCoverageRatio;
    private AdjustmentBasis adjustmentBasis;
    private DisplayStatus displayStatus;
    private Integer bestLag;        // 시차(일)
    private Double leadLagCorr;     // bestLag 기준 상관계수
    private Double lagConfidence;

    // relation 판단
    private Relation relation;      // LEADER / FOLLOWER / COINCIDENT / UNKNOWN

    private Double liquiditySimilarityScore;
    private Double volatilitySimilarityScore;

    // 순위 점수. 기존 score 별칭과 명시적 peerScore를 함께 유지한다.
    private Double score;
    private Double peerScore;

    public enum Relation {
        LEADER,
        FOLLOWER,
        COINCIDENT,
        UNKNOWN
    }

    public enum AdjustmentBasis {
        RAW_ONLY,
        SIMPLE_SUBTRACTION,
        FALLBACK_RAW
    }

    public enum DisplayStatus {
        SELECTED,
        ELIGIBLE_NOT_SELECTED,
        DISPLAY_ONLY,
        LOW_CORR,
        RAW_ONLY,
        ADJUSTED_ONLY,
        FALLBACK_RAW
    }
}
