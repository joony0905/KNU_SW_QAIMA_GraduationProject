package com.qaima.dto.peercluster;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RelativePointDto {
    private final LocalDateTime ts;
    private final Double pct; // 기준 대비 %
}
