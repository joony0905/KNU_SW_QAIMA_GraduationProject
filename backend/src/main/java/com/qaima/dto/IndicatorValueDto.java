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
/**
 * DB/영속화 전용 DTO.
 * API 관통 DTO 계약에는 사용하지 않는다.
 */
public class IndicatorValueDto {

    private OffsetDateTime ts; // t 필드 규칙과 구분되는 영속화 시각 컬럼

    private Freq freq;

    private String key;          // 예: "STOCHRSI_14_3_3", "BB_20_2"

    private BigDecimal valueNum; // 단일 값(옵션)

    private String valueJson;    // TODO: 확장 시 JsonNode 또는 Map<String, BigDecimal> 전환 고려
}
