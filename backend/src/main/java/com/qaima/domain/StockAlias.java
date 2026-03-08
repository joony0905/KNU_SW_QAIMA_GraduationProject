package com.qaima.domain;

import com.qaima.common.CompanyNameNormalizer;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "stock_alias",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_alias_stock_norm",
                        columnNames = {"stock_id", "normalized_alias"}
                )
        },
        indexes = {
                @Index(name = "idx_stock_alias_norm", columnList = "normalized_alias"),
                @Index(name = "idx_stock_alias_stock", columnList = "stock_id")
        }
)
public class StockAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alias_id")
    private Long aliasId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "alias_name", nullable = false, length = 255)
    private String aliasName;

    @Column(name = "normalized_alias", nullable = false, length = 255)
    private String normalizedAlias;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    @PreUpdate
    private void normalizeAlias() {
        this.normalizedAlias = CompanyNameNormalizer.normalizeKey(this.aliasName);
    }
}
