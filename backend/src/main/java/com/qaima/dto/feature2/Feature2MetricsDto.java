// backend/src/main/java/com/qaima/dto/feature2/Feature2MetricsDto.java
package com.qaima.dto.feature2;

import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.stock.StockMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class Feature2MetricsDto {

    private StockMeta stock;
    private IndustryMetaDto industry;

    private IndustryIndexBlockDto industryIndex;   // 추가
    private PeerClusterDto peerCluster;             // 추가
    private List<NewsItemDto> newsList;

    public static Feature2MetricsDto empty() {
        return Feature2MetricsDto.builder().build();
    }
}