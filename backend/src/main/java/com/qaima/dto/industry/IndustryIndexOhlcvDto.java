package com.qaima.dto.industry;

import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
public class IndustryIndexOhlcvDto {
    private final Long indexId;
    private final OffsetDateTime ts;
    private final Freq freq;
    private final BigDecimal open;
    private final BigDecimal high;
    private final BigDecimal low;
    private final BigDecimal close;
    private final BigDecimal volume;
}
