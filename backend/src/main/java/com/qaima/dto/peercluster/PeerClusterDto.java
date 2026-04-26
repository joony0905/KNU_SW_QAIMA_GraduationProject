// backend/src/main/java/com/qaima/dto/peercluster/PeerClusterDto.java
package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
@Builder
@lombok.extern.jackson.Jacksonized
public class PeerClusterDto {

    private String method;          // "INDUSTRY_CORR_V1"
    private Long industryId;
    private Freq freq;              // ONE_D / ONE_W
    private Integer window;         // 분석 윈도우
    private Integer peerCount;
    private Integer requestedPeerCount;
    private Integer effectivePeerCount;
    private Integer rawCandidateCount;
    private Integer evaluatedCandidateCount;
    private Integer eligibleCandidateCount;
    private Integer selectedPeerCount;
    private Integer displayedCandidateCount;
    private Integer displayLimit;
    private AdjustmentMethod adjustmentMethod;
    private String industryIndexCode;
    private String industryIndexName;
    private Integer adjustedReturnSampleSize;
    private Double adjustedReturnCoverageRatio;
    private Boolean adjustmentValid;
    private String adjustmentFallbackReason;
    private String anchorStockCode;
    private List<RelativePointDto> anchorSeries;
    private List<RelativePointDto> industryIndexSeries;
    // 대표 시계열
    private List<RelativePointDto> centroid;   // (ts, value)
    private List<BandPointDto> band;            // (ts, p20, p80), 선택값
    private List<RelativePointDto> peerCentroid;
    private List<BandPointDto> peerBand;

    // peer 목록. 최종 응답 계약이다.
    private List<PeerItemDto> peers;
    private List<PeerItemDto> candidates;

    private OffsetDateTime asOf;    // 계산 시각
    private String interpretationNote;

    public enum AdjustmentMethod {
        SIMPLE_SUBTRACTION,
        // 향후 잔차 기반 산업 요인 조정을 위해 예약한 값이다.
        BETA_RESIDUAL_RESERVED
    }
}
