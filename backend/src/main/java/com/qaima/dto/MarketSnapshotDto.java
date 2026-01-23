package com.qaima.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
