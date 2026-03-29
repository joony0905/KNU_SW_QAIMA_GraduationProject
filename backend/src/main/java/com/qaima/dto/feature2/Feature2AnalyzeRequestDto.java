package com.qaima.dto.feature2;

import com.qaima.domain.Freq;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature2AnalyzeRequestDto {

    private String stockCode;
    private Freq freq;
    private Integer window;
    private Integer peerCount;
    private Integer maxLag;
}
