package com.qaima.dto.ohlcv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OhlcvItemDto {
    private OffsetDateTime t;
    private BigDecimal o;
    private BigDecimal h;
    private BigDecimal l;
    private BigDecimal c;
    private BigDecimal v;
}
