package com.qaima.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "news_sentiment_observation", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_news_sentiment_observation",
                columnNames = {"news_id", "stock_code", "model_version", "prompt_version", "focus_text_version"}
        )
})
public class NewsSentimentObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "observation_id")
    private Long observationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private News news;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String url;

    @Column(length = 100)
    private String publisher;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "focus_text", nullable = false, columnDefinition = "longtext")
    private String focusText;

    @Column(name = "predicted_score", nullable = false, precision = 10, scale = 6)
    private BigDecimal predictedScore;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "focus_text_version", nullable = false, length = 64)
    private String focusTextVersion;

    @Column(name = "created_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    private OffsetDateTime createdAt;
}
