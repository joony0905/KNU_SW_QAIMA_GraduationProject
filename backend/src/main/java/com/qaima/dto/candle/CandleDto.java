package com.qaima.dto.candle;

import lombok.*;

@Getter
@AllArgsConstructor
public class CandleDto {
    private long t;   // epoch seconds (UTC)
    private double o;
    private double h;
    private double l;
    private double c;
    private long v;
}
