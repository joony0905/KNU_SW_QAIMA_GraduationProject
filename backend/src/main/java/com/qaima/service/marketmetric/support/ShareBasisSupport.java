package com.qaima.service.marketmetric.support;

import java.math.BigDecimal;

public final class ShareBasisSupport {

    private ShareBasisSupport() {
    }

    public static BigDecimal toBigDecimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    // Global shares_outstanding basis:
    // issued_shares_total - treasury_shares, treasury_shares is optional.
    public static BigDecimal sharesOutstanding(BigDecimal issuedSharesTotal, BigDecimal treasuryShares) {
        if (issuedSharesTotal == null) {
            return null;
        }
        if (treasuryShares == null) {
            return issuedSharesTotal;
        }
        BigDecimal outstanding = issuedSharesTotal.subtract(treasuryShares);
        return outstanding.signum() < 0 ? BigDecimal.ZERO : outstanding;
    }
}
