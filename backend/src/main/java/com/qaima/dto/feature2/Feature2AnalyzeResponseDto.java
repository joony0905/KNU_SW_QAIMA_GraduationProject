package com.qaima.dto.feature2;

import com.qaima.dto.industry.IndustryIndexMetaDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.industry.PeerClusterDto;
import com.qaima.dto.news.NewsItemDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Feature2AnalyzeResponseDto {
    private final IndustryMetaDto industry;
    private final IndustryIndexMetaDto industryIndex;
    private final PeerClusterDto peerCluster;
    private final List<NewsItemDto> news;
}
