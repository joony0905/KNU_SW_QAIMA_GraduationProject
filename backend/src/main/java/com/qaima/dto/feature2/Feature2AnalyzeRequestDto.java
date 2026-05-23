package com.qaima.dto.feature2;

import com.qaima.domain.Freq;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Feature2AnalyzeRequestDto {

    @NotBlank
    private String stockCode;
    private Freq freq;
    private Integer window;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private Integer peerCount;
    private Integer maxLag;
    private Integer displayLimit;
    private String llmVendor;
    private String investLevel;
}
