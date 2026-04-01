package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class PriceCacheEntry {
    private final BigDecimal price;
    private final Instant fetchedAt;
}
