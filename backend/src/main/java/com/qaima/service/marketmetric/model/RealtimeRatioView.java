package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;

public record RealtimeRatioView(
        BigDecimal price,
        BigDecimal marketCap,
        BigDecimal floatMarketCap,
        Double per,
        Double pbr,
        Double psr
) {
}
