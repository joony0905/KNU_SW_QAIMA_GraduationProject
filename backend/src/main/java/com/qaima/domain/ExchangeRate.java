package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "exchange_rate",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exchange_rate_pair_cycle_time_source",
                        columnNames = {"pair_code", "cycle", "raw_time", "source"}
                )
        },
        indexes = {
                @Index(name = "idx_exchange_rate_rate_date", columnList = "rate_date"),
                @Index(name = "idx_exchange_rate_pair_date", columnList = "pair_code, rate_date")
        }
)
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exchange_rate_id")
    private Long exchangeRateId;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "raw_time", nullable = false, length = 16)
    private String rawTime;

    @Column(nullable = false, length = 1)
    private String cycle;

    @Column(name = "pair_code", nullable = false, length = 20)
    private String pairCode;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(name = "rate_value", nullable = false, precision = 20, scale = 6)
    private BigDecimal rateValue;

    @Column(name = "unit_name", nullable = false, length = 20)
    private String unitName;

    @Column(name = "stat_code", nullable = false, length = 20)
    private String statCode;

    @Column(name = "stat_name", nullable = false, length = 255)
    private String statName;

    @Column(name = "item_code", nullable = false, length = 20)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, length = 50)
    @ColumnDefault("'BOK_ECOS'")
    private String source = "BOK_ECOS";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
