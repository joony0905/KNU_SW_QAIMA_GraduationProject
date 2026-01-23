package com.qaima.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WatchlistRequestDto {
    private String stockCode;
    @JsonAlias("exchange")
    private String exchangeCode;
    private Long watchlistId;
}
