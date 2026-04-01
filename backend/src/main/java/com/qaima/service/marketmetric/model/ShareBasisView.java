package com.qaima.service.marketmetric.model;

import java.math.BigDecimal;
import java.util.List;

public record ShareBasisView(
        BigDecimal sharesOutstanding,
        BigDecimal issuedSharesTotal,
        BigDecimal treasuryShares,
        BigDecimal floatingShares,
        List<String> warnings,
        String source
) {
}
