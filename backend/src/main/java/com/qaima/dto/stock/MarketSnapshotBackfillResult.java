package com.qaima.dto.stock;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSnapshotBackfillResult {

    private String stockCode;
    private String exchangeCode;
    private LocalDate asOfDate;
    private String status;
    private String message;
    private MarketSnapshotDto snapshot;
}
