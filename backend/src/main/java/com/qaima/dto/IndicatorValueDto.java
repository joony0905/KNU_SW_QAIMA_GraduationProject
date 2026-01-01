package com.qaima.dto;

import com.qaima.domain.Freq;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorValueDto {

    private OffsetDateTime ts;

    private Freq freq;

    private String key;          // 예: "STOCHRSI_14_3_3", "BB_20_2"

    private BigDecimal valueNum; // 단일 값(옵션)

    private String valueJson;    // k/d, upper/mid/lower 같은 JSON 문자열
}
