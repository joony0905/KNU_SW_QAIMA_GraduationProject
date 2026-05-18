package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "stock_institutional_holding_quarterly",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_inst_holding_quarterly_stock_period_source",
                        columnNames = {"stock_id", "report_period", "source"}
                )
        },
        indexes = {
                @Index(name = "idx_stock_inst_holding_quarterly_code_period", columnList = "stock_code, report_period"),
                @Index(name = "idx_stock_inst_holding_quarterly_period", columnList = "report_period")
        }
)
public class StockInstitutionalHoldingQuarterly {

    public static final String SOURCE = "SEC_13F_DATA_SET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_institutional_holding_quarterly_id")
    private Long stockInstitutionalHoldingQuarterlyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    @Column(name = "report_period", nullable = false)
    private LocalDate reportPeriod;

    @Column(length = 16)
    private String cusip;

    @Column(name = "institution_count", nullable = false)
    private Integer institutionCount = 0;

    @Column(name = "filing_row_count", nullable = false)
    private Integer filingRowCount = 0;

    @Column(name = "shares_held", nullable = false, precision = 30, scale = 0)
    private BigDecimal sharesHeld;

    @Column(name = "shares_change", precision = 30, scale = 0)
    private BigDecimal sharesChange;

    @Column(name = "shares_change_rate", precision = 20, scale = 8)
    private BigDecimal sharesChangeRate;

    @Column(name = "market_value_usd", nullable = false, precision = 30, scale = 0)
    private BigDecimal marketValueUsd;

    @Column(name = "shares_outstanding", precision = 30, scale = 0)
    private BigDecimal sharesOutstanding;

    @Column(name = "holding_ratio", precision = 20, scale = 8)
    private BigDecimal holdingRatio;

    @Column(nullable = false, length = 50)
    private String source = SOURCE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void applyDefaults() {
        if (source == null || source.isBlank()) {
            source = SOURCE;
        }
    }
}
