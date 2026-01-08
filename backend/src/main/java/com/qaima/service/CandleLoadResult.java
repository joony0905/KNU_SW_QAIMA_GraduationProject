package com.qaima.service;

import com.qaima.domain.CandleSource;
import com.qaima.domain.PriceOhlcv;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CandleLoadResult {
    private final List<PriceOhlcv> candles;
    private final CandleSource source;
}
