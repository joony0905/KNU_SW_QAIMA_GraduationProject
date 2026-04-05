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

    private Freq freq;

    private String from;

    private String to;

    private String marketDivCode;

    private Boolean includeExplain;

    private String llmVendor;
}
