package com.qaima.dto;

import com.qaima.domain.Freq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatOneRequestDto {
    private String stockCode;
    private Freq freq;

    // 차트 / 재무데이터 FastAPI에 넘길 때 쓰는 페이로드
    private List<OhlcvItemDto> ohlcv;
    private List<FinancialSummaryDto> financials;

    private Boolean includeExplain;

    // 나중에 확장용 (예: user risk profile, lang 등)
    private Map<String, Object> options;
}
