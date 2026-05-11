package com.qaima.dto.feature3;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record Feature3OverlayCachePreviewRequestDto(
        Long portfolioId,
        List<String> stockCodes,
        @NotNull List<String> selectedOverlays,
        String cachePolicy
) {
}
