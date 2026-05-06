package com.qaima.dto.feature3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PortfolioAnalyzeRequestDto(
        @NotEmpty List<@Valid PortfolioHoldingRequestDto> holdings,
        List<String> options,
        @DecimalMin("0.0") @DecimalMax("1.0") Double riskGamma
) {
}
