package com.qaima.repository;

import java.util.List;
import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {
    List<WatchlistItem> findByWatchlist(Watchlist watchlist);
}
