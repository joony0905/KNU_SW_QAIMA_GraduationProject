package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeerItemDto {
    private String stockCode;
    private String companyName;
    private Double score; // corr/similarity (optional)
}
