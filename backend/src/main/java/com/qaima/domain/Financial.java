package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Setter
@Entity
@Table(
        name = "financial",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_fiscal_period",
                        columnNames = {"stock_id", "fiscal_year", "period_type", "period_no"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_financials_stock_fiscal_period",
                        columnList = "stock_id, period_type, fiscal_year, period_no"
                )
        }
)
public class Financial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long financialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    /**
     * 신규 표준 키 (NOT NULL)
     * Q: 1~4, H: 1~2, A: 1, TTM: 0
     */
    @Column(name = "period_no", nullable = false)
    private int periodNo = 1;

    @Column(name = "fiscal_quarter")
    private Integer fiscalQuarter;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 10, nullable = false)
    private PeriodType periodType;

    private LocalDate filingDate;

    @Column(length = 10)
    private String currency;

    @Column(length = 50)
    private String source;

    @Column(precision = 20, scale = 2)
    private BigDecimal revenue;

    @Column(precision = 20, scale = 2)
    private BigDecimal grossProfit;

    @Column(precision = 20, scale = 2)
    private BigDecimal operatingIncome;

    @Column(precision = 20, scale = 2)
    private BigDecimal netIncome;

    @Column(precision = 20, scale = 2)
    private BigDecimal assets;

    @Column(precision = 20, scale = 2)
    private BigDecimal liabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal equity;

    @Column(precision = 20, scale = 2)
    private BigDecimal capitalStock;

    @Column(precision = 20, scale = 2)
    private BigDecimal retainedEarnings;

    @Column(precision = 20, scale = 2)
    private BigDecimal cashAndEquivalents;

    @Column(precision = 20, scale = 2)
    private BigDecimal marketCap;

    @Column(precision = 10, scale = 4)
    private BigDecimal operatingMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal netMargin;

    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(precision = 10, scale = 4)
    private BigDecimal per;

    @Column(precision = 10, scale = 4)
    private BigDecimal pbr;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void syncPeriodNo() {
        if (periodType == null) return;

        switch (periodType) {
            case A -> {
                this.periodNo = 1;
                this.fiscalQuarter = null;
            }
            case TTM -> {
                this.periodNo = 0;
                this.fiscalQuarter = null;
            }
            case Q -> {
                // quarter가 있으면 그걸 최우선
                if (this.fiscalQuarter != null && this.fiscalQuarter >= 1 && this.fiscalQuarter <= 4) {
                    this.periodNo = this.fiscalQuarter;
                } else {
                    // quarter가 없으면 기존 periodNo가 1~4면 유지, 아니면 1
                    if (this.periodNo < 1 || this.periodNo > 4) this.periodNo = 1;
                    // 필요하면 호환성을 위해 quarter도 맞춰줌
                    this.fiscalQuarter = this.periodNo;
                }
            }
            case H -> {
                // CSV 서비스에서 periodNo(1/2)를 넣었다면 그대로 유지
                if (this.periodNo == 1 || this.periodNo == 2) {
                    // ok
                } else {
                    // 없으면 reportDate 월로 유추
                    int m = (this.reportDate != null) ? this.reportDate.getMonthValue() : 1;
                    this.periodNo = (m <= 6) ? 1 : 2;
                }
                this.fiscalQuarter = null;
            }
        }
    }
}
