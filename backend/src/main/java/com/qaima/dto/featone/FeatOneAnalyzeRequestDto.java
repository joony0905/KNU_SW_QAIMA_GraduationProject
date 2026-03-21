package com.qaima.dto.featone;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class FeatOneAnalyzeRequestDto {
    private String stockCode;
    private Freq freq;
    private String from;
    private String to;
    private String marketDivCode;
    private Boolean includeExplain;
}