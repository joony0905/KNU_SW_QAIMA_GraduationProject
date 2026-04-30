package com.qaima.dto.credit;

import com.qaima.domain.CreditLedgerType;
import com.qaima.domain.CreditReferenceType;
import java.time.Instant;

public record CreditLedgerDto(
        Long ledgerId,
        Long amount,
        Long balanceAfter,
        CreditLedgerType type,
        String reason,
        CreditReferenceType referenceType,
        String referenceId,
        Instant createdAt
) {
}
