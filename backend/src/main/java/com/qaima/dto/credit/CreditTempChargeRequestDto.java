package com.qaima.dto.credit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreditTempChargeRequestDto(
        @NotNull @Positive Long amount,
        String reason
) {
}
