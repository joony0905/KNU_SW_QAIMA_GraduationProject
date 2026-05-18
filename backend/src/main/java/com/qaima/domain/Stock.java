package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Setter
@Entity
@Table(name = "stock", uniqueConstraints = {
        // ERD: (exchange_id, stock_code) [unique]
        @UniqueConstraint(
                name = "uk_exchange_stock_code",
                columnNames = {"exchange_id", "stock_code"}
        )
})
        
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockId;

    // (ERD: exchange_id bigint [not null, ref: > exchange.exchange_id])
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @Column(nullable = false, length = 32)
    private String stockCode;

    @Column(length = 20)
    private String isin;

    @Column(nullable = false, length = 255)
    private String companyName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    // (ERD: industry_id bigint [ref: > industry.industry_id])
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id")
    private Industry industry;

    @Column(length = 20)
    private String assetType;

    @Column(length = 3)
    @ColumnDefault("'KRW'")
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "share_class", nullable = false, length = 20)
    @ColumnDefault("'OTHER'")
    private ShareClass shareClass;

    @Column(name = "dart_corp_code", length = 8)
    private String dartCorpCode;

    @Column(name = "dart_corp_name", length = 255)
    private String dartCorpName;

    @Column(name = "dart_modified_date")
    private LocalDate dartModifiedDate;

    @Column(name = "dart_synced_at")
    private Instant dartSyncedAt;

    @Column(name = "sec_cik", length = 10)
    private String secCik;

    @Column(name = "sec_company_name", length = 255)
    private String secCompanyName;

    @Column(name = "sec_synced_at")
    private Instant secSyncedAt;

    @Column(name = "sec_issued_shares_synced_at")
    private Instant secIssuedSharesSyncedAt;

    private LocalDate listedAt;
    private LocalDate delistedAt;

    @PrePersist
    @PreUpdate
    private void applyDefaults() {
        if (shareClass == null) {
            shareClass = ShareClass.OTHER;
        }
    }

}
