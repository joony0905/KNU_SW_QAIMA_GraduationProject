package com.qaima.dto;

import com.qaima.domain.Watchlist;
import lombok.Getter;

@Getter
public class WatchlistDto {

    private Long watchlistId;
    private String name;
    private String sortPref;

    public WatchlistDto(Watchlist w) {
        this.watchlistId = w.getWatchlistId();
        this.name = w.getName();
        this.sortPref = w.getSortPref();
    }
}

