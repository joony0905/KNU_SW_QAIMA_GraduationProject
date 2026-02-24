package com.qaima.dto.industry;

import com.qaima.dto.candle.CandleDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IndustryIndexBlockDto {
    private final IndustryIndexMetaDto meta;
    private final List<CandleDto> ohlcv; // 렌더링 표준으로 통일
}