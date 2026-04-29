package com.qaima.service.feature2.model;

import com.qaima.domain.Freq;

public record Feature2Command(
        String stockCode,
        Freq freq,
        int window,
        int peerCount,
        int maxLag,
        int displayLimit,
        String llmVendor
) {
}
