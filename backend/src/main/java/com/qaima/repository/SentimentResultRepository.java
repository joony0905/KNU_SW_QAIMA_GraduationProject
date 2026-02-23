package com.qaima.repository;

import com.qaima.domain.SentimentResult;
import com.qaima.domain.SentimentResultId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentimentResultRepository extends JpaRepository<SentimentResult, SentimentResultId> {
}
