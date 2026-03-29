package com.qaima.dto.featone;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import com.qaima.dto.financial.FinancialSummaryDto;
import com.qaima.dto.ohlcv.OhlcvItemDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FeatOneRequestDto {
    private String stockCode;
    private Freq freq;

    // 차트 / 재무데이터 FastAPI에 넘길 때 쓰는 페이로드
    private List<OhlcvItemDto> ohlcv;
    private List<FinancialSummaryDto> financials;

    private Boolean includeExplain;
}
