package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * TODO: 펀더멘털 슬롯(valuation/growth/profitability) 전용 DTO로 유지한다.
 * TODO: Object 타입은 추후 Map<String, BigDecimal> 형태로 고정하는 것을 권장한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorSlotsDto {
    private Object valuation;
    private Object growth;
    private Object profitability;
}
