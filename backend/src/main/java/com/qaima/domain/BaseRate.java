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
        name = "base_rate",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_base_rate_cycle_time_stat_item",
                        columnNames = {"cycle", "raw_time", "stat_code", "item_code"}
                )
        },
        indexes = {
                @Index(name = "idx_base_rate_base_date", columnList = "base_date"),
                @Index(name = "idx_base_rate_stat_item_base_date", columnList = "stat_code, item_code, base_date")
        }
)
public class BaseRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "base_rate_id")
    private Long baseRateId;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "raw_time", nullable = false, length = 16)
    private String rawTime;

    @Column(nullable = false, length = 1)
    private String cycle;

    @Column(name = "rate_value", nullable = false, precision = 10, scale = 6)
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
