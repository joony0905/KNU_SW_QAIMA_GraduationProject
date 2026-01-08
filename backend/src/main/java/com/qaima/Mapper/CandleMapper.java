package com.qaima.mapper;

import com.qaima.domain.PriceOhlcv;
import com.qaima.dto.CandleDto;

import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public class CandleMapper {

    public static CandleDto toDto(PriceOhlcv e) {
        return new CandleDto(
                e.getId().getTs().toEpochSecond(),
                e.getOpen().doubleValue(),
                e.getHigh().doubleValue(),
                e.getLow().doubleValue(),
                e.getClose().doubleValue(),
                e.getVolume() == null
                        ? 0L
                        : e.getVolume().setScale(0, RoundingMode.DOWN).longValue()
        );
    }

    public static List<CandleDto> toSeries(List<PriceOhlcv> list) {
        return list.stream()
                .map(CandleMapper::toDto)
                .sorted(Comparator.comparingLong(CandleDto::getT))
                .toList();
    }
}
