package com.qaima.dto.industry;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BandPointDto {
    private LocalDateTime ts;
    private Double lower;
    private Double upper;
}
