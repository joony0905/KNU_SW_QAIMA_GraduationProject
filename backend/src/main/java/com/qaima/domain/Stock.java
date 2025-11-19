package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
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

    // (ERD: industry_id bigint [ref: > industry.industry_id])
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id")
    private Industry industry;

    @Column(length = 20)
    private String assetType;

    @Column(length = 3)
    @ColumnDefault("'KRW'")
    private String currency;

    private LocalDate listedAt;
    private LocalDate delistedAt;
}
