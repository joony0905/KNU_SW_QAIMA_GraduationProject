package com.qaima.dto;

import com.qaima.domain.Freq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneAnalyzeRequestDto {
    private String stockCode;
    private Freq freq;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String marketDivCode;
    private Boolean includeExplain;
}
