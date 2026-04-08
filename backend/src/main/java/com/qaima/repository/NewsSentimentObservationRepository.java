package com.qaima.repository;

import com.qaima.domain.NewsSentimentObservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsSentimentObservationRepository extends JpaRepository<NewsSentimentObservation, Long> {
}
