package com.qaima.dto.feature2;

import com.qaima.domain.Freq;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature2AnalyzeRequestDto {

    @NotBlank
    private String stockCode;
    private Freq freq;
    private Integer window;
    private Integer peerCount;
    private Integer maxLag;
    private Integer displayLimit;
}
