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
        name = "market_investor_flow",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_investor_flow_market_industry_date_source",
                        columnNames = {"market_code", "industry_code", "trade_date", "source"}
                )
        },
        indexes = {
                @Index(name = "idx_market_investor_flow_market_date", columnList = "market_code, trade_date"),
                @Index(name = "idx_market_investor_flow_trade_date", columnList = "trade_date")
        }
)
public class MarketInvestorFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_investor_flow_id")
    private Long marketInvestorFlowId;

    @Column(name = "market_code", nullable = false, length = 16)
    private String marketCode;

    @Column(name = "industry_code", nullable = false, length = 32)
    private String industryCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "foreign_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal foreignNetBuyQty;

    @Column(name = "foreign_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal foreignNetBuyValueMillion;

    @Column(name = "individual_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal individualNetBuyQty;

    @Column(name = "individual_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal individualNetBuyValueMillion;

    @Column(name = "institution_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal institutionNetBuyQty;

    @Column(name = "institution_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal institutionNetBuyValueMillion;

    @Column(nullable = false, length = 50)
    @ColumnDefault("'KIS'")
    private String source = "KIS";

    @Column(name = "source_tr_id", nullable = false, length = 20)
    @ColumnDefault("'FHPTJ04040000'")
    private String sourceTrId = "FHPTJ04040000";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
