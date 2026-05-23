package com.qaima.service.batch;

import java.util.List;

public record BatchSummary(
        int target,
        int success,
        int skipped,
        int empty,
        int timeLimited,
        int failed,
        int retried,
        int retryRecovered,
        int savedRows,
        List<String> failedTargets
) {
    public String toLogString() {
        return "target=%d success=%d skipped=%d empty=%d timeLimited=%d failed=%d retried=%d retryRecovered=%d savedRows=%d failedTargets=%s"
                .formatted(target, success, skipped, empty, timeLimited, failed, retried, retryRecovered, savedRows, failedTargets);
    }
}
