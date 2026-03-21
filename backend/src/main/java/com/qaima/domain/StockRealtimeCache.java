package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stock_realtime_cache")
public class StockRealtimeCache {

    @Id
    @Column(name = "stock_id")
    private Long stockId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private OffsetDateTime ts;

    @Column(precision = 18, scale = 6)
    private BigDecimal prevClose;

    @Column(precision = 18, scale = 6)
    private BigDecimal last;

    @Column(precision = 10, scale = 6)
    private BigDecimal changeRatio;

    @Column(precision = 20, scale = 0)
    private BigDecimal volume;

    @Column(precision = 20, scale = 6)
    private BigDecimal turnover;
}
