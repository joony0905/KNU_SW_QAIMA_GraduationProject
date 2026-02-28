package com.qaima.dto.industry;

import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class PeerClusterDto {
    private String method;                 // 예: "INDUSTRY_CORR_V1"
    private Long industryId;
    private Freq freq;                     // D/W ...
    private Integer window;                // 90 등
    private Integer peerCount;

    private List<RelativePointDto> centroid;   // 대표선 (ts, pct)
    private List<BandPointDto> band;           // optional (ts, lower, upper)
    private List<PeerItemDto> peers;           // optional (code, name, score)

    private OffsetDateTime asOf;               // 계산/캐시 기준 시각
}