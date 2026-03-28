package com.qaima.dto.peercluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@lombok.extern.jackson.Jacksonized
public class RelativePointDto {

    @JsonProperty("t")
    private final OffsetDateTime ts;

    @JsonProperty("value")
    private final Double pct;
}