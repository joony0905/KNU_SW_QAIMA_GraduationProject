package com.qaima.dto.watchlist;

import com.qaima.domain.Stock;
import com.qaima.domain.WatchlistItem;
import lombok.Getter;

@Getter
public class WatchlistResponseDto {

    private Long watchlistItemId;
    private Long watchlistId;
    private Long stockId;
    private String stockCode;
    private String stockName;
    private String exchangeCode;
    private String note;
    private String industryName;

    public WatchlistResponseDto(WatchlistItem item) {
        this.watchlistItemId = item.getWatchlistItemId();
        this.note = item.getNote();

        if (item.getWatchlist() != null) {
            this.watchlistId = item.getWatchlist().getWatchlistId();
        }

        Stock stock = item.getStock();
        if (stock != null) {
            this.stockId = stock.getStockId();
            this.stockCode = stock.getStockCode();
            this.stockName = stock.getCompanyName();

            if (stock.getExchange() != null) {
                this.exchangeCode = stock.getExchange().getCode();
            }
            if (stock.getIndustry() != null) {
                this.industryName = stock.getIndustry().getName();
            }
        }
    }
}
