package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sentiment_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sentiment_result", columnNames = {"news_id", "model"})
})
public class SentimentResult {

    @EmbeddedId
    private SentimentResultId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("newsId")
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal score;

    @Column(length = 10)
    private String label;

    @Column(name = "created_at")
    @ColumnDefault("now()")
    private OffsetDateTime createdAt;
}
