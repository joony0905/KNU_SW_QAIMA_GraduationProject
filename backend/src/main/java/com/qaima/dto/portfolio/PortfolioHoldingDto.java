package com.qaima.dto.portfolio;

import com.qaima.domain.PortfolioHolding;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PortfolioHoldingDto(
        @NotBlank String stockCode,
        @NotBlank String stockName,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal averagePrice
) {
    public static PortfolioHoldingDto from(PortfolioHolding holding) {
        return new PortfolioHoldingDto(
                holding.getStockCode(),
                holding.getStockName(),
                holding.getQuantity(),
                holding.getAveragePrice()
        );
    }
}
