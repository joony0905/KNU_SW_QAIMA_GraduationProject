package com.qaima.dto.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record PortfolioSaveRequestDto(
        @NotNull @PositiveOrZero BigDecimal cashAmount,
        List<@Valid PortfolioHoldingDto> holdings
) {
}
