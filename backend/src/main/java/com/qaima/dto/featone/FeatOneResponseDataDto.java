package com.qaima.dto.featone;
import com.qaima.dto.financial.FinancialSummaryDto;
import com.qaima.dto.indicator.IndicatorValueDto;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import lombok.*;

import java.util.List;

//FastAPI 분석 텍스트 + DB 데이터들을 한 번에 묶어서 프론트로 보내기 위한 최종 Response DTO
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneResponseDataDto {

    private StockDto stock;

    // 차트
    private List<PriceOhlcvDto> candles;

    // 지표
    // 초기버전에선 null -> python에서 ohlcv계산해서 직접 채운기
    private List<IndicatorValueDto> indicators;

    // 재무 요약 (최근 N개) - finan에서 의미 있는 지표만
    private List<FinancialSummaryDto> financials;

    // LLM 분석 결과 (FastAPI → GPT)
    private FeatOneResponseTextDto analysis;


}
