package com.qaima.service.marketmetric.model;

import java.util.List;

public record MarketMetricResult(
        SnapshotMetricView snapshot,
        RealtimeRatioView realtime,
        List<String> warnings,
        boolean fallbackUsed
) {
}
