package com.qaima.dto.feature2;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.stock.StockMeta;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Feature2ExplainMetricsDto {
    private StockMeta stock;
    private IndustryMetaDto industry;
    private Feature2MetricsDto.BaseRateMetrics baseRate;
    private Feature2MetricsDto.ShortSellingMetrics shortSelling;
    private Feature2MetricsDto.BaseRateTrendSummary baseRateTrendSummary;
    private Feature2MetricsDto.ShortSellingTrendSummary shortSellingTrendSummary;
    private IndustryIndexSummary industryIndex;
    private PeerClusterSummary peerClusterSummary;
    private Feature2MetricsDto.NewsSentimentSummary newsSentimentSummary;
    private List<RecentNewsSummary> recentNews;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class IndustryIndexSummary {
        private Long indexId;
        private String name;
        private String code;
        private String currency;
        private Integer seriesPointCount;
        private Double firstValue;
        private Double lastValue;
        private Double returnPct;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PeerClusterSummary {
        private String method;
        private Long industryId;
        private String anchorStockCode;
        private Integer requestedPeerCount;
        private Integer effectivePeerCount;
        private Integer rawCandidateCount;
        private Integer evaluatedCandidateCount;
        private Integer eligibleCandidateCount;
        private Integer selectedPeerCount;
        private Integer displayedCandidateCount;
        private Integer displayLimit;
        private String adjustmentMethod;
        private String industryIndexName;
        private Boolean adjustmentValid;
        private String adjustmentFallbackReason;
        private Integer leaderCount;
        private Integer followerCount;
        private Integer coincidentCount;
        private Integer unknownRelationCount;
        private Integer lowCorrCandidateCount;
        private PeerChartSummary chartSummary;
        private List<PeerItemSummary> topPeers;
        private List<PeerItemSummary> displayCandidates;
        private String interpretationNote;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PeerChartSummary {
        private Integer seriesPointCount;
        private Double anchorReturnPct;
        private Double industryIndexReturnPct;
        private Double peerCentroidReturnPct;
        private Double peerBandWidthLast;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PeerItemSummary {
        private String stockCode;
        private String companyName;
        private Double corr;
        private Double adjustedCorr;
        private Double corrStability;
        private Integer bestLag;
        private Double leadLagCorr;
        private Double lagConfidence;
        private String relation;
        private Double liquiditySimilarityScore;
        private Double volatilitySimilarityScore;
        private Double peerScore;
        private String displayStatus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RecentNewsSummary {
        private Long newsId;
        private String title;
        private String publisher;
        private String publishedAt;
        private BigDecimal sentimentScore;
    }
}
