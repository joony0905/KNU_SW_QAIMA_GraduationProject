package com.qaima.repository;

import com.qaima.domain.NewsSentimentObservation;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsSentimentObservationRepository extends JpaRepository<NewsSentimentObservation, Long> {

    boolean existsByStockCodeAndTitleAndPublishedAtAndModelVersionAndInputFormatVersion(
            String stockCode,
            String title,
            OffsetDateTime publishedAt,
            String modelVersion,
            String inputFormatVersion
    );

    boolean existsByStockCodeAndTitleAndModelVersionAndInputFormatVersion(
            String stockCode,
            String title,
            String modelVersion,
            String inputFormatVersion
    );
}
