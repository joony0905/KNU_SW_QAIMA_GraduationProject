package com.qaima.service.feature2.model;

import com.qaima.domain.Freq;
import java.time.OffsetDateTime;

public record Feature2Command(
        String stockCode,
        Freq freq,
        int window,
        OffsetDateTime from,
        OffsetDateTime to,
        int peerCount,
        int maxLag,
        int displayLimit,
        String llmVendor,
        String investLevel
) {
}
