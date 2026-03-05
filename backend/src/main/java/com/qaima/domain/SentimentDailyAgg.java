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
@Table(name = "sentiment_daily_agg")
public class SentimentDailyAgg {

    @EmbeddedId
    private SentimentDailyAggId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stockId")
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("industryId")
    @JoinColumn(name = "industry_id")
    private Industry industry;

    private Integer cntTotal;
    private Integer cntPos;
    private Integer cntNeg;
    private Integer cntNeu;

    @Column(precision = 10, scale = 6)
    private BigDecimal scoreAvg;

    @Column(precision = 10, scale = 6)
    private BigDecimal scoreStd;

    @Column(name = "updated_at")
    @ColumnDefault("now()")
    private OffsetDateTime updatedAt;
}
