package com.qaima.repository;

import com.qaima.domain.StockRealtimeCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRealtimeCacheRepository extends JpaRepository<StockRealtimeCache, Long> {
}
