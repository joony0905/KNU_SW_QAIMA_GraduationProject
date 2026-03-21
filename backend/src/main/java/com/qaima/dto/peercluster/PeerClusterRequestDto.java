package com.qaima.dto.peercluster;

import com.qaima.domain.Freq;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeerClusterRequestDto {

    private Long industryId;
    private String anchorStockCode;
    private Freq freq;
    private Integer window;
    private Integer peerCount;
    private Integer maxLag;
}