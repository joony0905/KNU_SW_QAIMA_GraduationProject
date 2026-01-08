package com.qaima.dto;

import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class CandleSeriesResponse {

    private String stockCode;
    private String freq;
    private String source;   // DB | KIS | MARKETSTACK | MIXED
    private String timezone; // "UTC"
    private List<CandleDto> data;
}
