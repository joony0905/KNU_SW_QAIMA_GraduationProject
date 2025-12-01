package com.qaima.dto;

import com.qaima.domain.Stock;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneResponseDataDto {

    private String stockCode;
    private String companyName;
    private String exchangeCode;

    // 차트
    private List<PriceOhlcvDto> candles;

    // 지표
    // 초기버전에선 null -> python에서 ohlcv계산해서 직접 채운기
    private List<IndicatorValueDto> indicators;

    // 재무 요약 (최근 N개) - finan에서 의미 있는 지표만
    private List<FinancialSummaryDto> financials;

    // LLM 분석 결과 (FastAPI → GPT) 아직 없으면 null
    private String analysisText;
}
