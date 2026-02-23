package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class PeerClusterDto {
    private final Long industryId;
    private final OffsetDateTime ts;
    private final String method;
    private final String paramsJson;
    private final String clusterJson;
    private final List<PeerClusterItemDto> items;
}
