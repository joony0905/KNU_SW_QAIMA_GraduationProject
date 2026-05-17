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
        name = "sec_13f_holding",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_sec_13f_holding_accession_stock_cusip",
                        columnNames = {"accession_number", "stock_id", "cusip"}
                )
        },
        indexes = {
                @Index(name = "idx_sec_13f_holding_stock_period", columnList = "stock_id, report_period"),
                @Index(name = "idx_sec_13f_holding_cusip_period", columnList = "cusip, report_period"),
                @Index(name = "idx_sec_13f_holding_manager_period", columnList = "manager_cik, report_period")
        }
)
public class Sec13fHolding {

    public static final String SOURCE = "SEC_13F_DATA_SET";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sec_13f_holding_id")
    private Long sec13fHoldingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sec_13f_filing_id", nullable = false)
    private Sec13fFiling filing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "accession_number", nullable = false, length = 32)
    private String accessionNumber;

    @Column(name = "manager_cik", nullable = false, length = 10)
    private String managerCik;

    @Column(name = "report_period", nullable = false)
    private LocalDate reportPeriod;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(nullable = false, length = 16)
    private String cusip;

    @Column(name = "name_of_issuer", length = 255)
    private String nameOfIssuer;

    @Column(name = "title_of_class", length = 120)
    private String titleOfClass;

    @Column(name = "filing_row_count", nullable = false)
    private Integer filingRowCount = 1;

    @Column(nullable = false, precision = 30, scale = 0)
    private BigDecimal shares;

    @Column(name = "value_raw", nullable = false, precision = 30, scale = 0)
    private BigDecimal valueRaw;

    @Column(name = "value_unit", nullable = false, length = 20)
    private String valueUnit;

    @Column(name = "market_value_usd", nullable = false, precision = 30, scale = 0)
    private BigDecimal marketValueUsd;

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
        if (filingRowCount == null) {
            filingRowCount = 1;
        }
    }
}
