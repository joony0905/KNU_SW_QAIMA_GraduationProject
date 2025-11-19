package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Setter
@Entity
@Table(name = "financial",
      uniqueConstraints = {
    @UniqueConstraint(
            name = "uk_stock_report_version",
            columnNames = {"stock_id", "report_date", "version"}
    )
},
indexes = {
@Index(name = "idx_financials_stock_fiscal_period", columnList = "stock_id, fiscal_year, fiscal_quarter")
    })

public class Financial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long financialId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false, columnDefinition = "int default 1")
    private int version;

    @Column(nullable = false)
    private int fiscalYear;

    private Integer fiscalQuarter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodType periodType;

    private LocalDate filingDate;
    private String currency;
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

    // --- [파생] 지표 필드들 ---
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
        
}
