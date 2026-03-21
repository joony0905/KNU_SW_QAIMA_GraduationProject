package com.qaima.dto.ohlcv;

import com.qaima.domain.Freq;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOhlcvDto {

    private OffsetDateTime ts;

    private Freq freq;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
}
