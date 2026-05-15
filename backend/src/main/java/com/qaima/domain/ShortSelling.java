package com.qaima.domain;

import jakarta.persistence.*;
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
        name = "short_selling",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_short_selling_stock_date",
                        columnNames = {"stock_id", "report_date"}
                )
        },
        indexes = {
                @Index(name = "idx_short_selling_report_date", columnList = "report_date"),
                @Index(name = "idx_short_selling_market_date", columnList = "market_code, report_date")
        }
)
public class ShortSelling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "short_selling_id")
    private Long shortSellingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "market_code", nullable = false, length = 20)
    private String marketCode;

    @Column(name = "security_type", nullable = false, length = 30)
    private String securityType;

    @Column(name = "short_volume_total", precision = 24, scale = 6)
    private BigDecimal shortVolumeTotal;

    @Column(name = "short_volume_uptick_applied", precision = 24, scale = 6)
    private BigDecimal shortVolumeUptickApplied;

    @Column(name = "short_volume_uptick_exempt", precision = 24, scale = 6)
    private BigDecimal shortVolumeUptickExempt;

    @Column(name = "total_volume", precision = 24, scale = 6)
    private BigDecimal totalVolume;

    @Column(name = "short_volume_ratio", precision = 10, scale = 6)
    private BigDecimal shortVolumeRatio;

    @Column(name = "short_amount_total", precision = 20, scale = 0)
    private BigDecimal shortAmountTotal;

    @Column(name = "short_amount_uptick_applied", precision = 20, scale = 0)
    private BigDecimal shortAmountUptickApplied;

    @Column(name = "short_amount_uptick_exempt", precision = 20, scale = 0)
    private BigDecimal shortAmountUptickExempt;

    @Column(name = "total_amount", precision = 20, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "short_amount_ratio", precision = 10, scale = 6)
    private BigDecimal shortAmountRatio;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_screen_id", nullable = false, length = 20)
    private String sourceScreenId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void applyDefaults() {
        if (source == null || source.isBlank()) {
            source = "KRX";
        }
        if (sourceScreenId == null || sourceScreenId.isBlank()) {
            sourceScreenId = "MDCSTAT301";
        }
    }
}
