package com.qaima.domain;

import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "stock", uniqueConstraints = {
        // indexes { (exchange_id, stock_code) [unique] } -> 복합 유니크 키
        @UniqueConstraint(
                name = "uk_exchange_stock_code",
                columnNames = {"exchange_id", "stock_code"}
        )
})
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // bigserial [pk]
    private Long stockId;

    // exchange_id bigint [not null, ref: > exchange.exchange_id]
    // Exchange 엔티티를 만들고 나면 @ManyToOne으로 변경 필요
    @Column(nullable = false)
    private Long exchangeId;

    // industry_id bigint [ref: > industry.industry_id]
    // Industry 엔티티를 만들고 나면 @ManyToOne으로 변경 필요
    private Long industryId;

    @Column(nullable = false, length = 32) // stock_code varchar(32) [not null]
    private String stockCode;

    @Column(length = 20)
    private String isin;

    @Column(nullable = false, length = 255) // company_name varchar(255) [not null]
    private String companyName;

    @Column(length = 20)
    private String assetType;

    @Column(length = 3)
    @ColumnDefault("'KRW'") // currency varchar(3) [default: 'KRW']
    private String currency;

    private LocalDate listedAt;
    private LocalDate delistedAt;

}
