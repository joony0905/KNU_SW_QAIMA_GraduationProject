package com.qaima.domain;

import com.qaima.domain.converter.PeriodTypeConverter;
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
                        name = "uk_financial_stock_period",
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
     * Q: 1~4, H: 1~2, A: 1, TTM: 0
     */
    @Column(name = "period_no", nullable = false)
    private int periodNo = 1;

    @Column(name = "fiscal_quarter")
    private Integer fiscalQuarter;

    @Convert(converter = PeriodTypeConverter.class)
    @Column(name = "period_type", length = 10, nullable = false)
    private PeriodType periodType;

    @Column(name = "filing_date")
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
    private BigDecimal currentAssets;

    @Column(precision = 20, scale = 2)
    private BigDecimal liabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal currentLiabilities;

    @Column(precision = 20, scale = 2)
    private BigDecimal equity;

    @Column(precision = 20, scale = 2)
    private BigDecimal capitalStock;

    @Column(precision = 20, scale = 2)
    private BigDecimal retainedEarnings;

    @Column(precision = 20, scale = 2)
    private BigDecimal cashAndEquivalents;

    @Column(precision = 20, scale = 2)
    private BigDecimal accountsReceivable;

    @Column(precision = 20, scale = 2)
    private BigDecimal inventories;

    @Column(precision = 20, scale = 2)
    private BigDecimal shortTermBorrowings;

    @Column(precision = 20, scale = 2)
    private BigDecimal currentPortionOfLongTermBorrowings;

    @Column(precision = 20, scale = 2)
    private BigDecimal longTermBorrowings;

    @Column(precision = 20, scale = 2)
    private BigDecimal operatingCashFlow;

    @Column(precision = 20, scale = 2)
    private BigDecimal investingCashFlow;

    @Column(precision = 20, scale = 2)
    private BigDecimal financingCashFlow;

    @Column(precision = 20, scale = 2)
    private BigDecimal interestExpense;

    @Column(precision = 20, scale = 2)
    private BigDecimal capexPpe;

    @Column(precision = 20, scale = 2)
    private BigDecimal capexIntangible;

    @Column(precision = 20, scale = 2)
    private BigDecimal depreciationExpense;

    @Column(precision = 20, scale = 2)
    private BigDecimal amortizationExpense;

    @Column(precision = 20, scale = 2)
    private BigDecimal incomeTaxExpense;

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
        if (periodType == null) {
            return;
        }

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
                if (this.fiscalQuarter != null && this.fiscalQuarter >= 1 && this.fiscalQuarter <= 4) {
                    this.periodNo = this.fiscalQuarter;
                } else {
                    if (this.periodNo < 1 || this.periodNo > 4) {
                        this.periodNo = 1;
                    }
                    this.fiscalQuarter = this.periodNo;
                }
            }
            case H -> {
                if (this.periodNo == 1 || this.periodNo == 2) {
                    // keep
                } else {
                    int month = (this.reportDate != null) ? this.reportDate.getMonthValue() : 1;
                    this.periodNo = (month <= 6) ? 1 : 2;
                }
                this.fiscalQuarter = null;
            }
        }
    }
}