package com.qaima.dto.feature3;

import java.util.List;

public record Feature3OverlayCachePreviewResponseDto(
        Integer coreCredit,
        Integer overlayRefreshCredit,
        Integer totalCredit,
        List<OverlayCacheItem> overlays,
        String userMessage
) {
    public record OverlayCacheItem(
            String overlayType,
            String cacheStatus,
            Integer additionalCredit,
            Boolean userConfirmationRequired,
            String userMessage,
            String sourceCacheStatus,
            String cacheAsOf,
            String timezone,
            Boolean policySelectable
    ) {
    }
}
