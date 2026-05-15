package com.qaima.dto.finra;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinraShortSaleVolumeRow(
        LocalDate tradeDate,
        String symbol,
        BigDecimal shortVolume,
        BigDecimal shortExemptVolume,
        BigDecimal totalVolume,
        String market
) {
}
