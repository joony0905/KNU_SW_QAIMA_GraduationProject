package com.qaima.dto.peercluster;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.domain.Freq;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerClusterRequestDto {

    private Long industryId;
    private String anchorStockCode;
    private Freq freq;
    private Integer window;
    private OffsetDateTime from;
    private OffsetDateTime to;
    private Integer peerCount;
    private Integer maxLag;
    private Integer displayLimit;
}
