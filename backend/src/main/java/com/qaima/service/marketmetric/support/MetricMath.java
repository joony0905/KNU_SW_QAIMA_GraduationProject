package com.qaima.service.marketmetric.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MetricMath {

    private static final int RATIO_SCALE = 8;

    private MetricMath() {
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal ratioPercent(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal base = divide(numerator, denominator);
        return base == null ? null : base.multiply(BigDecimal.valueOf(100));
    }

    public static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
