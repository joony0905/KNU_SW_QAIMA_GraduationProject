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
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class Feature2MetricsDto {

    private StockMeta stock;
    private IndustryMetaDto industry;
    private IndustryIndexBlockDto industryIndex;
    private PeerClusterDto peerCluster;
    private ShortSellingMetrics shortSelling;
    private BaseRateMetrics baseRate;
    private BaseRateTrendSummary baseRateTrendSummary;
    private ShortSellingTrendSummary shortSellingTrendSummary;
    private List<NewsItemDto> newsList;
    private NewsSentimentSummary newsSentimentSummary;

    public static Feature2MetricsDto empty() {
        return Feature2MetricsDto.builder().build();
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
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
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class BaseRateMetrics {
        private LocalDate date;
        private BigDecimal value;
        private String unit;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class BaseRateTrendSummary {
        private Integer window;
        private Integer pointCount;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal startValue;
        private BigDecimal endValue;
        private BigDecimal change;
        private String direction;
        private String unit;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class ShortSellingTrendSummary {
        private Integer window;
        private Integer pointCount;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal startShortVolumeRatio;
        private BigDecimal endShortVolumeRatio;
        private BigDecimal shortVolumeRatioChange;
        private BigDecimal avgShortVolumeRatio;
        private BigDecimal maxShortVolumeRatio;
        private BigDecimal startShortAmountRatio;
        private BigDecimal endShortAmountRatio;
        private BigDecimal shortAmountRatioChange;
        private BigDecimal avgShortAmountRatio;
        private BigDecimal maxShortAmountRatio;
        private String direction;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class NewsSentimentSummary {
        private LocalDate summaryDate;
        private BigDecimal dailyAvgScore;
        private Integer dailyNewsCount;
        private Integer scoredNewsCount;
        private Integer positiveCount;
        private Integer neutralCount;
        private Integer negativeCount;
        private BigDecimal strongestPositiveScore;
        private BigDecimal strongestNegativeScore;
        private List<RecentNewsSentiment> recentItems;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class RecentNewsSentiment {
        private Long newsId;
        private String title;
        private String publisher;
        private java.time.OffsetDateTime publishedAt;
        private BigDecimal sentimentScore;
        private String sentimentLabel;
    }
}
