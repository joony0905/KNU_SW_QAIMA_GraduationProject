package com.qaima.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorSnapshotDto {

    private String stockCode;
    private LocalDate asOfDate;

    private String periodType;
    private Integer fiscalYear;
    private Integer periodNo;
    private LocalDate reportDate;

    private Double per;
    private Double pbr;
    private Double roe;
    private Double operatingMargin;
    private Double netMargin;
    private Double debtRatio;
}
