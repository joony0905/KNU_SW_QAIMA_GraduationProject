package com.qaima.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class StockMeta {
    private String stockCode;
    private String companyName;
    private String exchangeCode;
    private String countryCode;
    private String currency;
    private BigDecimal price;
    private BigDecimal changeRate;
    private String source;

    // === Feature2 확장 ===
    private String sectorCode;     // idx_bztp_mcls_cd
    private String sectorName;     // idx_bztp_mcls_cd_name
    private String industryCode;   // idx_bztp_scls_cd
    private String industryName;   // idx_bztp_scls_cd_name
    private Boolean kospi200;      // kospi200_item_yn == "Y"
}
