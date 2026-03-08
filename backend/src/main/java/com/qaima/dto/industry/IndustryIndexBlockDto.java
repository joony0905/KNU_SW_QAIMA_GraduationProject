package com.qaima.dto.industry;

import com.qaima.dto.peercluster.RelativePointDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IndustryIndexBlockDto {

    private final Long indexId;
    private final String name;
    private final String code;
    private final String currency;

    // 시각화/비교용 (%)
    private final List<RelativePointDto> series;
}