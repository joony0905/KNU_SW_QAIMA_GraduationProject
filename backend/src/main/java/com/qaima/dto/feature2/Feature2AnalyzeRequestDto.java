package com.qaima.dto.feature2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature2AnalyzeRequestDto {

    @JsonProperty("stockCode")
    private String stockCode;

    @JsonProperty("freq")
    private Freq freq;

    @JsonProperty("window")
    private Integer window;

    @JsonProperty("peerCount")
    private Integer peerCount;

    @JsonProperty("maxLag")
    private Integer maxLag;
}