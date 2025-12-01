package com.qaima.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneRequestDto {

    private String stockCode;
    private String companyName;

    // 차트 / 지표 / 재무데이터 FastAPI에 넘길 때 쓰는 페이로드
    private List<PriceOhlcvDto> candles;
    private List<IndicatorValueDto> indicators;
    private List<FinancialSummaryDto> financials;

    // 나중에 확장용 (예: user risk profile, lang 등)
    private Map<String, Object> options;
}
