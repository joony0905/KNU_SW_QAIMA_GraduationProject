package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PeerClusterItemDto {
    private final Long stockId;
    private final String stockCode;
    private final Integer cluster;
}
