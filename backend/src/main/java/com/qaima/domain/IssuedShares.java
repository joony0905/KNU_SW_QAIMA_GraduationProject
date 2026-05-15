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
        name = "issued_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_issued_shares_stock_date_type",
                        columnNames = {"stock_id", "base_date", "share_type"}
                )
        },
        indexes = {
                @Index(name = "idx_issued_shares_stock_date", columnList = "stock_id, base_date"),
                @Index(name = "idx_issued_shares_corp_code_date", columnList = "corp_code, base_date")
        }
)
public class IssuedShares {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issued_shares_id")
    private Long issuedSharesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    // OpenDART 공시 접수번호
    @Column(name = "rcept_no", nullable = false, length = 32)
    private String rceptNo;

    // 법인 구분(Y: 유가, K: 코스닥, N: 코넥스 등)
    @Column(name = "corp_cls", length = 10)
    private String corpCls;

    // OpenDART 회사 고유 코드
    @Column(name = "corp_code", nullable = false, length = 10)
    private String corpCode;

    // OpenDART 회사명
    @Column(name = "corp_name", length = 255)
    private String corpName;

    // 주식 구분(보통주, 우선주, 합계 등)
    @Column(name = "share_type", nullable = false, length = 50)
    private String shareType;

    // 발행할 주식의 총수
    @Column(name = "authorized_shares")
    private Long authorizedShares;

    // 현재까지 발행한 주식의 총수
    @Column(name = "issued_shares_to_date")
    private Long issuedSharesToDate;

    // 현재까지 감소한 주식의 총수
    @Column(name = "decreased_shares_to_date")
    private Long decreasedSharesToDate;

    // 감자로 감소한 주식수
    @Column(name = "decreased_by_reduction")
    private Long decreasedByReduction;

    // 이익소각으로 감소한 주식수
    @Column(name = "decreased_by_profit_incineration")
    private Long decreasedByProfitIncineration;

    // 상환으로 감소한 주식수
    @Column(name = "decreased_by_redemption")
    private Long decreasedByRedemption;

    // 기타 사유로 감소한 주식수
    @Column(name = "decreased_by_other")
    private Long decreasedByOther;

    // 기준일 시점 발행주식 총수
    @Column(name = "issued_shares_total")
    private Long issuedSharesTotal;

    // 자기주식수
    @Column(name = "treasury_shares")
    private Long treasuryShares;

    // 유통주식수
    @Column(name = "floating_shares")
    private Long floatingShares;

    // 발행주식수 기준일
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    // 데이터 출처(OpenDART)
    @Column(nullable = false, length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void applyDefaults() {
        if (source == null || source.isBlank()) {
            source = "OPENDART";
        }
    }
}
