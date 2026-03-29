package com.qaima.dto.featone;

import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneAnalyzeRequestDto {

    private String stockCode;

    @JsonProperty("freq")
    private Freq freq;

    @JsonProperty("from")
    private String from;

    @JsonProperty("to")
    private String to;

    @JsonProperty("market_div_code")
    @JsonAlias("marketDivCode")
    private String marketDivCode;

    @JsonProperty("include_explain")
    @JsonAlias("includeExplain")
    private Boolean includeExplain;
}
