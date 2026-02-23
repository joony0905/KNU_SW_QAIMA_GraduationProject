package com.qaima.repository;

import com.qaima.domain.SentimentDailyAgg;
import com.qaima.domain.SentimentDailyAggId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentimentDailyAggRepository extends JpaRepository<SentimentDailyAgg, SentimentDailyAggId> {
}
