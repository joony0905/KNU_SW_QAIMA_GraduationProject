package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.stock.StockMeta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature2MetricsDto {

    private StockMeta stock;
    private IndustryMetaDto industry;
    private IndustryIndexBlockDto industryIndex;
    private PeerClusterDto peerCluster;
    private ShortSellingMetrics shortSelling;
    private BaseRateMetrics baseRate;
    private List<NewsItemDto> newsList;

    public static Feature2MetricsDto empty() {
        return Feature2MetricsDto.builder().build();
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ShortSellingMetrics {
        private Long shortSellingId;
        private Long stockId;
        private String stockCode;
        private String companyName;
        private LocalDate reportDate;
        private String marketCode;
        private String securityType;
        private BigDecimal shortVolumeTotal;
        private BigDecimal shortVolumeUptickApplied;
        private BigDecimal shortVolumeUptickExempt;
        private BigDecimal totalVolume;
        private BigDecimal shortVolumeRatio;
        private BigDecimal shortAmountTotal;
        private BigDecimal shortAmountUptickApplied;
        private BigDecimal shortAmountUptickExempt;
        private BigDecimal totalAmount;
        private BigDecimal shortAmountRatio;
        private String source;
        private String sourceScreenId;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class BaseRateMetrics {
        private LocalDate date;
        private BigDecimal value;
        private String unit;
    }
}
