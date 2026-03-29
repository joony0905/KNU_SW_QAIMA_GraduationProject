package com.qaima.dto.feature2;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature2AnalyzeRequestDto {

    // External JSON contract is snake_case.
    // Temporary compatibility: accept legacy camelCase payloads from existing clients.
    @JsonProperty("stock_code")
    @JsonAlias("stockCode")
    private String stockCode;

    @JsonProperty("freq")
    private Freq freq;

    @JsonProperty("window")
    private Integer window;

    @JsonProperty("peer_count")
    @JsonAlias("peerCount")
    private Integer peerCount;

    @JsonProperty("max_lag")
    @JsonAlias("maxLag")
    private Integer maxLag;
}
