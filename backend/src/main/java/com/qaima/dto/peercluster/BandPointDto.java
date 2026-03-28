package com.qaima.dto.peercluster;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@lombok.extern.jackson.Jacksonized
public class BandPointDto {

    @JsonProperty("t")
    private final OffsetDateTime ts;

    private final Double p20;
    private final Double p80;
}