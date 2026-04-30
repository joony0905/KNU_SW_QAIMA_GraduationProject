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
        name = "bond_yield",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bond_yield_source_instrument_cycle_time",
                        columnNames = {"source", "instrument_code", "cycle", "raw_time"}
                )
        },
        indexes = {
                @Index(name = "idx_bond_yield_yield_date", columnList = "yield_date"),
                @Index(name = "idx_bond_yield_instrument_date", columnList = "instrument_code, yield_date"),
                @Index(name = "idx_bond_yield_country_date", columnList = "country_code, yield_date")
        }
)
public class BondYield {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bond_yield_id")
    private Long bondYieldId;

    @Column(name = "yield_date", nullable = false)
    private LocalDate yieldDate;

    @Column(name = "raw_time", nullable = false, length = 16)
    private String rawTime;

    @Column(nullable = false, length = 1)
    private String cycle;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "instrument_code", nullable = false, length = 20)
    private String instrumentCode;

    @Column(name = "instrument_name", nullable = false, length = 100)
    private String instrumentName;

    @Column(name = "maturity_months")
    private Integer maturityMonths;

    @Column(name = "yield_value", nullable = false, precision = 10, scale = 6)
    private BigDecimal yieldValue;

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
