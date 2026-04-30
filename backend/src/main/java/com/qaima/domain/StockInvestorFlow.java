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
        name = "stock_investor_flow",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_investor_flow_stock_date_source",
                        columnNames = {"stock_id", "trade_date", "source"}
                )
        },
        indexes = {
                @Index(name = "idx_stock_investor_flow_code_date", columnList = "stock_code, trade_date"),
                @Index(name = "idx_stock_investor_flow_trade_date", columnList = "trade_date")
        }
)
public class StockInvestorFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_investor_flow_id")
    private Long stockInvestorFlowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "stock_code", nullable = false, length = 32)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "market_div_code", nullable = false, length = 8)
    @ColumnDefault("'J'")
    private String marketDivCode = "J";

    @Column(name = "close_price", precision = 20, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "accumulated_volume", precision = 24, scale = 0)
    private BigDecimal accumulatedVolume;

    @Column(name = "accumulated_trading_value_million", precision = 24, scale = 0)
    private BigDecimal accumulatedTradingValueMillion;

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

    @Column(name = "securities_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal securitiesNetBuyQty;

    @Column(name = "securities_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal securitiesNetBuyValueMillion;

    @Column(name = "investment_trust_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal investmentTrustNetBuyQty;

    @Column(name = "investment_trust_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal investmentTrustNetBuyValueMillion;

    @Column(name = "private_fund_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal privateFundNetBuyQty;

    @Column(name = "private_fund_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal privateFundNetBuyValueMillion;

    @Column(name = "bank_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal bankNetBuyQty;

    @Column(name = "bank_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal bankNetBuyValueMillion;

    @Column(name = "insurance_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal insuranceNetBuyQty;

    @Column(name = "insurance_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal insuranceNetBuyValueMillion;

    @Column(name = "fund_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal fundNetBuyQty;

    @Column(name = "fund_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal fundNetBuyValueMillion;

    @Column(name = "other_net_buy_qty", precision = 24, scale = 0)
    private BigDecimal otherNetBuyQty;

    @Column(name = "other_net_buy_value_million", precision = 24, scale = 0)
    private BigDecimal otherNetBuyValueMillion;

    @Column(nullable = false, length = 50)
    @ColumnDefault("'KIS'")
    private String source = "KIS";

    @Column(name = "source_tr_id", nullable = false, length = 20)
    @ColumnDefault("'FHPTJ04160001'")
    private String sourceTrId = "FHPTJ04160001";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
