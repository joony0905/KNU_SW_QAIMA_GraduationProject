package com.qaima.dto.feature3;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PortfolioHoldingRequestDto(
        @NotBlank String stockCode,
        @Positive Double quantity,
        @Positive Double avgPrice
) {
}
