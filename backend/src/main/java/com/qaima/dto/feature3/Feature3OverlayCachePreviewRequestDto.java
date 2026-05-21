package com.qaima.dto.feature3;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record Feature3OverlayCachePreviewRequestDto(
        Long portfolioId,
        List<String> stockCodes,
        @NotNull List<String> selectedOverlays,
        String cachePolicy,
        Map<String, String> overlayCachePolicies
) {
}
