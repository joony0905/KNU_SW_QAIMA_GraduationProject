package com.qaima.dto.feature2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Feature2AnalyzeRequestDto {
    @JsonProperty("stockCode")
    private String stockCode;
}