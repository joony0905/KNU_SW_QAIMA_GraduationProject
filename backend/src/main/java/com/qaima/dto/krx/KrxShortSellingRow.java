package com.qaima.dto.krx;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KrxShortSellingRow(
        LocalDate tradeDate,
        String stockCode,
        String companyName,
        String marketCode,
        String securityType,
        BigDecimal shortVolumeTotal,
        BigDecimal shortVolumeUptickApplied,
        BigDecimal shortVolumeUptickExempt,
        BigDecimal totalVolume,
        BigDecimal shortVolumeRatio,
        BigDecimal shortAmountTotal,
        BigDecimal shortAmountUptickApplied,
        BigDecimal shortAmountUptickExempt,
        BigDecimal totalAmount,
        BigDecimal shortAmountRatio
) {
}
