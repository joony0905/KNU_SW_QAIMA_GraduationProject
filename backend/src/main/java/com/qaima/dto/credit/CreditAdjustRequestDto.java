package com.qaima.dto.credit;

import jakarta.validation.constraints.NotNull;

public record CreditAdjustRequestDto(
        @NotNull Long userId,
        @NotNull Long amount,
        String reason
) {
}
