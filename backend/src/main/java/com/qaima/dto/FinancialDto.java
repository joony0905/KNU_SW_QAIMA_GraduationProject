package com.qaima.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDto {

    private Long stockId;
    private String ticker;
    private String companyName;
    private Integer year;
    private Double per;
    private Double pbr;
    private Double roe;
    private Double debtRatio;        // 부채비율 (%) ** 매핑 할 때 liabilities / equity * 100
    private Double operatingMargin;  // 영업이익률 (%)
}
