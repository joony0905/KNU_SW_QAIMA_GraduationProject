package com.qaima.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WatchlistRequestDto {
    private Long stockId;
    private Long watchlistId;
}
