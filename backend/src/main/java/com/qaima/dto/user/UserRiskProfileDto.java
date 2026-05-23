package com.qaima.dto.user;

import java.math.BigDecimal;

public record UserRiskProfileDto(
        BigDecimal defaultRiskGamma,
        String profileType
) {
}
