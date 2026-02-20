package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshotBackfillResult {
    private String stockCode;
    private String exchangeCode;
    private LocalDate asOfDate;
    private String status; // UPDATED, SKIPPED, FAILED, NOT_ELIGIBLE
    private String message;
    private MarketSnapshotDto snapshot;
}
