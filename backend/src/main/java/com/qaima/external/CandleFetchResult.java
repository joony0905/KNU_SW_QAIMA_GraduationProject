package com.qaima.external;

import com.qaima.domain.CandleSource;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class CandleFetchResult {
    private final List<PriceOhlcvDto> candles;
    private final CandleSource source;
}
