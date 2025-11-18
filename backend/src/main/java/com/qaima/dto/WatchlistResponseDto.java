package com.qaima.dto;

import com.qaima.domain.WatchlistItem;
import lombok.Getter;

@Getter
public class WatchlistResponseDto {
    private Long watchlistItemId;
    private Long stockId;
    private String stockName;
    private String note;

    public WatchlistResponseDto(WatchlistItem item) {
        this.watchlistItemId = item.getWatchlistItemId();
        this.stockId = item.getStock().getStockId();
        this.stockName = item.getStock().getCompanyName();
        this.note = item.getNote();
    }
}
