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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
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
        name = "stock_security_identifier",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stock_security_identifier_stock_type_value",
                        columnNames = {"stock_id", "identifier_type", "identifier_value"}
                )
        },
        indexes = {
                @Index(name = "idx_stock_security_identifier_type_value", columnList = "identifier_type, identifier_value"),
                @Index(name = "idx_stock_security_identifier_stock", columnList = "stock_id")
        }
)
public class StockSecurityIdentifier {

    public static final String TYPE_CUSIP = "CUSIP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_security_identifier_id")
    private Long stockSecurityIdentifierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "identifier_type", nullable = false, length = 20)
    private String identifierType;

    @Column(name = "identifier_value", nullable = false, length = 32)
    private String identifierValue;

    @Column(name = "issuer_name", length = 255)
    private String issuerName;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private Integer confidence = 100;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (identifierType != null) {
            identifierType = identifierType.trim().toUpperCase();
        }
        if (identifierValue != null) {
            identifierValue = identifierValue.trim().toUpperCase();
        }
        if (source == null || source.isBlank()) {
            source = "MANUAL";
        }
        if (confidence == null) {
            confidence = 100;
        }
        if (active == null) {
            active = true;
        }
    }
}
