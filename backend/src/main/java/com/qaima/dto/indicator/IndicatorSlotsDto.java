package com.qaima.dto.indicator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


// 추후 지표성 확인이 필요할수도 있어 일단 남겨놓음
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
