package com.qaima.dto;

import com.qaima.domain.Stock;
import com.qaima.domain.WatchlistItem;
import lombok.Getter;

@Getter

public class WatchlistResponseDto {

    private Long watchlistItemId;
    private Long stockId;
    private String stockName;
    private String note;
    private String industryName;

    public WatchlistResponseDto(WatchlistItem item) {

        this.watchlistItemId = item.getWatchlistItemId();
        this.note = item.getNote();

        Stock stock = item.getStock();
        if (stock != null) {
            this.stockId = stock.getStockId();
            this.stockName = stock.getCompanyName();

            if (stock.getIndustry() != null) {
                this.industryName = stock.getIndustry().getName();
            }
        }
    }
}
