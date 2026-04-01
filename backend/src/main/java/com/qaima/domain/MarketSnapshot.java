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
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "market_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_market_snapshot_stock_date",
                        columnNames = {"stock_id", "as_of_date"}
                )
        },
        indexes = {
                @Index(name = "idx_market_snapshot_stock_date", columnList = "stock_id, as_of_date")
        }
)
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // 스냅샷 기준일
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    // 시가총액 = 현재가 * 발행주식수
    @Column(name = "market_cap", precision = 20, scale = 0)
    private BigDecimal marketCap;

    // 유통시가총액 = 현재가 * 유통주식수
    @Column(name = "float_market_cap", precision = 20, scale = 0)
    private BigDecimal floatMarketCap;

    // 주가수익비율
    @Column(precision = 10, scale = 4)
    private BigDecimal per;

    // 주가순자산비율
    @Column(precision = 10, scale = 4)
    private BigDecimal pbr;

    // 유통주식수 / 발행주식수
    @Column(name = "float_ratio", precision = 10, scale = 4)
    private BigDecimal floatRatio;

    // 자기주식수 / 발행주식수
    @Column(name = "treasury_ratio", precision = 10, scale = 4)
    private BigDecimal treasuryRatio;

    // 계산에 사용한 기준 발행주식수
    @Column(name = "shares_outstanding", precision = 20, scale = 0)
    private BigDecimal sharesOutstanding;

    // 최근 12개월 기준 주당순이익
    @Column(name = "eps_ttm", precision = 20, scale = 6)
    private BigDecimal epsTtm;

    // 주당순자산
    @Column(name = "bps", precision = 20, scale = 6)
    private BigDecimal bps;

    // 주당매출
    @Column(name = "sps", precision = 20, scale = 6)
    private BigDecimal sps;

    // 수익성/안정성 지표
    @Column(name = "roe", precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(name = "roa", precision = 10, scale = 4)
    private BigDecimal roa;

    @Column(name = "operating_margin", precision = 10, scale = 4)
    private BigDecimal operatingMargin;

    @Column(name = "net_margin", precision = 10, scale = 4)
    private BigDecimal netMargin;

    @Column(name = "debt_ratio", precision = 10, scale = 4)
    private BigDecimal debtRatio;

    // TODO: OpenDART ETL 확장 시 사용
    @Column(name = "current_assets", precision = 20, scale = 2)
    private BigDecimal currentAssets;

    @Column(name = "current_liabilities", precision = 20, scale = 2)
    private BigDecimal currentLiabilities;

    @Column(name = "inventory", precision = 20, scale = 2)
    private BigDecimal inventory;

    @Column(name = "interest_expense", precision = 20, scale = 2)
    private BigDecimal interestExpense;

    @Column(name = "operating_cash_flow", precision = 20, scale = 2)
    private BigDecimal operatingCashFlow;

    @Column(name = "capex", precision = 20, scale = 2)
    private BigDecimal capex;

    @Column(name = "warning_flags", length = 255)
    private String warningFlags;

    // 값 산출 기준 출처(OPENDART_PRIMARY, KIS_FALLBACK 등)
    @Column(length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
