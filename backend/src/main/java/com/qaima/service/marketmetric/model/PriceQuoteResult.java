package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;
import java.util.List;

public record PriceQuoteResult(
        BigDecimal price,
        List<String> warnings,
        boolean staleUsed
) {
}
