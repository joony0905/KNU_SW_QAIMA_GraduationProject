package com.qaima.dto.watchlist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WatchlistRequestDto {
    @NotNull(message = "stockId는 필수입니다.")
    @Positive(message = "stockId는 1 이상이어야 합니다.")
    private Long stockId;

    @NotNull(message = "watchlistId는 필수입니다.")
    @Positive(message = "watchlistId는 1 이상이어야 합니다.")
    private Long watchlistId;
}
