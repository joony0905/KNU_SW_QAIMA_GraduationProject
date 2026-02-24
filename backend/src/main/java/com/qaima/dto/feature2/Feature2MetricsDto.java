// backend/src/main/java/com/qaima/dto/feature2/Feature2MetricsDto.java
package com.qaima.dto.feature2;

import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.stock.StockMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class Feature2MetricsDto {

    private StockMeta stock;
    private IndustryMetaDto industry;

    // 이후 단계에서 붙일 필드:
    // private IndustryIndexBlockDto industryIndex;
    // private List<PeerClusterItemDto> peers;
    // private List<NewsItemDto> newsList;

    public static Feature2MetricsDto empty() {
        return Feature2MetricsDto.builder().build();
    }
}