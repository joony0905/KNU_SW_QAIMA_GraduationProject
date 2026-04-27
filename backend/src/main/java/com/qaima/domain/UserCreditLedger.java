package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "user_credit_ledger",
        indexes = {
                @Index(name = "idx_user_credit_ledger_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_user_credit_ledger_reference", columnList = "reference_type, reference_id")
        }
)
public class UserCreditLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CreditLedgerType type;

    @Column(length = 100)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", length = 30)
    private CreditReferenceType referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
