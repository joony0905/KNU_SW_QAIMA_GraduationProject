package com.qaima.dto.portfolio;

import com.qaima.domain.Portfolio;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record PortfolioResponseDto(
        Long portfolioId,
        BigDecimal cashAmount,
        List<PortfolioHoldingDto> holdings
) {
    public static PortfolioResponseDto from(Portfolio portfolio) {
        return new PortfolioResponseDto(
                portfolio.getPortfolioId(),
                portfolio.getCashAmount(),
                portfolio.getHoldings().stream()
                        .sorted(Comparator.comparing(holding -> holding.getPosition() == null ? 0 : holding.getPosition()))
                        .map(PortfolioHoldingDto::from)
                        .toList()
        );
    }

    public static PortfolioResponseDto empty() {
        return new PortfolioResponseDto(null, BigDecimal.ZERO, List.of());
    }
}
