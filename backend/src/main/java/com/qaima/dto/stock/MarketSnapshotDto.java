package com.qaima.dto.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSnapshotDto {

    private LocalDate asOfDate;
    private BigDecimal marketCap;
    private Double per;
    private Double pbr;
    private BigDecimal sharesOutstanding;
    private String source;
}
