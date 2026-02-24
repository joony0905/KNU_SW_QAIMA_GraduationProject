package com.qaima.dto.feature2;

import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.industry.PeerClusterDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.stock.StockMeta;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class Feature2MetricsDto {
    private final StockMeta stock;                 // 권장: 최소 메타(프론트/로그 일관)
    private final IndustryMetaDto industry;           // 기존 재사용
    private final IndustryIndexBlockDto industryIndex;// meta + ohlcv
    private final PeerClusterDto peerCluster;         // 기존 재사용
    private final List<NewsItemDto> newsList;         // 기존 재사용 (sentimentScore 포함돼있다는 전제)
}