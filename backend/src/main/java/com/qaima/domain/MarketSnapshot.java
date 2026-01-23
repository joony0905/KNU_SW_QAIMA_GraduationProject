package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "market_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_snapshot_stock_date",
                        columnNames = {"stock_id", "as_of_date"}
                )
        },
        indexes = {
                @Index(name = "idx_market_snapshot_stock_date", columnList = "stock_id, as_of_date")
        }
)
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "market_cap", precision = 20, scale = 0)
    private BigDecimal marketCap;

    @Column(precision = 10, scale = 4)
    private BigDecimal per;

    @Column(precision = 10, scale = 4)
    private BigDecimal pbr;

    @Column(name = "shares_outstanding", precision = 20, scale = 0)
    private BigDecimal sharesOutstanding;

    @Column(length = 50)
    private String source;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
