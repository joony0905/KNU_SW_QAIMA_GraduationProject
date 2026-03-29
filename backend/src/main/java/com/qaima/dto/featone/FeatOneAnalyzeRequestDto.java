package com.qaima.dto.featone;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneAnalyzeRequestDto {

    // External JSON contract is snake_case.
    // Temporary compatibility: accept legacy camelCase payloads from existing clients.
    @JsonProperty("stock_code")
    @JsonAlias("stockCode")
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
