package com.qaima.service.feature2.support;

import com.qaima.dto.feature2.Feature2ExplainMetricsDto;
import com.qaima.dto.feature2.Feature2MacroRatesSeriesDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.peercluster.BandPointDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.peercluster.PeerItemDto;
import com.qaima.dto.peercluster.RelativePointDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Feature2ExplainMetricsAssembler {

    private static final int TOP_PEER_LIMIT = 8;
    private static final int DISPLAY_CANDIDATE_LIMIT = 8;
    private static final int NEWS_LIMIT = 5;

    public Feature2ExplainMetricsDto from(Feature2MetricsDto metrics) {
        if (metrics == null) {
            return Feature2ExplainMetricsDto.builder().build();
        }

        return Feature2ExplainMetricsDto.builder()
                .stock(metrics.getStock())
                .industry(metrics.getIndustry())
                .baseRate(metrics.getBaseRate())
                .macroRates(metrics.getMacroRates())
                .macroTrendSummaries(toMacroTrendSummaries(metrics.getMacroRatesSeries()))
                .stockInvestorFlowSummary(metrics.getInvestorFlow() == null ? null : metrics.getInvestorFlow().getStockSummary())
                .marketInvestorFlowSummary(metrics.getInvestorFlow() == null ? null : metrics.getInvestorFlow().getMarketSummary())
                .shortSelling(metrics.getShortSelling())
                .baseRateTrendSummary(metrics.getBaseRateTrendSummary())
                .shortSellingTrendSummary(metrics.getShortSellingTrendSummary())
                .industryIndex(toIndustryIndexSummary(metrics.getIndustryIndex()))
                .peerClusterSummary(toPeerClusterSummary(metrics.getPeerCluster()))
                .newsSentimentSummary(metrics.getNewsSentimentSummary())
                .recentNews(toRecentNews(metrics.getNewsList()))
                .build();
    }

    private List<Feature2ExplainMetricsDto.MacroTrendSummary> toMacroTrendSummaries(Feature2MacroRatesSeriesDto seriesDto) {
        if (seriesDto == null || seriesDto.getSeries() == null || seriesDto.getSeries().isEmpty()) {
            return List.of();
        }
        return seriesDto.getSeries().stream()
                .filter(series -> series != null && series.getPoints() != null && !series.getPoints().isEmpty())
                .map(this::toMacroTrendSummary)
                .toList();
    }

    private Feature2ExplainMetricsDto.MacroTrendSummary toMacroTrendSummary(Feature2MacroRatesSeriesDto.SeriesBlock series) {
        List<Feature2MacroRatesSeriesDto.Point> points = series.getPoints();
        Feature2MacroRatesSeriesDto.Point first = points.get(0);
        Feature2MacroRatesSeriesDto.Point last = points.get(points.size() - 1);
        BigDecimal start = first == null ? null : first.getValue();
        BigDecimal end = last == null ? null : last.getValue();
        BigDecimal change = start == null || end == null ? null : end.subtract(start);
        return Feature2ExplainMetricsDto.MacroTrendSummary.builder()
                .key(series.getKey())
                .label(series.getLabel())
                .unit(series.getUnit())
                .pointCount(points.size())
                .startDate(first == null || first.getDate() == null ? null : first.getDate().toString())
                .endDate(last == null || last.getDate() == null ? null : last.getDate().toString())
                .startValue(start)
                .endValue(end)
                .change(change)
                .direction(direction(change))
                .build();
    }

    private String direction(BigDecimal change) {
        if (change == null) {
            return "UNKNOWN";
        }
        int sign = change.compareTo(BigDecimal.ZERO);
        if (sign > 0) {
            return "UP";
        }
        if (sign < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    private Feature2ExplainMetricsDto.IndustryIndexSummary toIndustryIndexSummary(IndustryIndexBlockDto index) {
        if (index == null) {
            return null;
        }
        List<RelativePointDto> series = index.getSeries() == null ? List.of() : index.getSeries();
        return Feature2ExplainMetricsDto.IndustryIndexSummary.builder()
                .indexId(index.getIndexId())
                .name(index.getName())
                .code(index.getCode())
                .currency(index.getCurrency())
                .seriesPointCount(series.size())
                .firstValue(firstPct(series))
                .lastValue(lastPct(series))
                .returnPct(returnPct(series))
                .build();
    }

    private Feature2ExplainMetricsDto.PeerClusterSummary toPeerClusterSummary(PeerClusterDto cluster) {
        if (cluster == null) {
            return null;
        }
        List<PeerItemDto> peers = cluster.getPeers() == null ? List.of() : cluster.getPeers();
        List<PeerItemDto> candidates = cluster.getCandidates() == null ? List.of() : cluster.getCandidates();

        return Feature2ExplainMetricsDto.PeerClusterSummary.builder()
                .method(cluster.getMethod())
                .industryId(cluster.getIndustryId())
                .anchorStockCode(cluster.getAnchorStockCode())
                .requestedPeerCount(cluster.getRequestedPeerCount())
                .effectivePeerCount(cluster.getEffectivePeerCount())
                .rawCandidateCount(cluster.getRawCandidateCount())
                .evaluatedCandidateCount(cluster.getEvaluatedCandidateCount())
                .eligibleCandidateCount(cluster.getEligibleCandidateCount())
                .selectedPeerCount(cluster.getSelectedPeerCount())
                .displayedCandidateCount(cluster.getDisplayedCandidateCount())
                .displayLimit(cluster.getDisplayLimit())
                .adjustmentMethod(cluster.getAdjustmentMethod() == null ? null : cluster.getAdjustmentMethod().name())
                .industryIndexName(cluster.getIndustryIndexName())
                .adjustmentValid(cluster.getAdjustmentValid())
                .adjustmentFallbackReason(cluster.getAdjustmentFallbackReason())
                .leaderCount(countRelation(peers, PeerItemDto.Relation.LEADER))
                .followerCount(countRelation(peers, PeerItemDto.Relation.FOLLOWER))
                .coincidentCount(countRelation(peers, PeerItemDto.Relation.COINCIDENT))
                .unknownRelationCount(countRelation(peers, PeerItemDto.Relation.UNKNOWN))
                .lowCorrCandidateCount(countDisplayStatus(candidates, PeerItemDto.DisplayStatus.LOW_CORR))
                .chartSummary(toPeerChartSummary(cluster))
                .topPeers(toPeerSummaries(peers, TOP_PEER_LIMIT))
                .displayCandidates(toPeerSummaries(candidates, DISPLAY_CANDIDATE_LIMIT))
                .interpretationNote(cluster.getInterpretationNote())
                .build();
    }

    private Feature2ExplainMetricsDto.PeerChartSummary toPeerChartSummary(PeerClusterDto cluster) {
        List<RelativePointDto> peerCentroid = prefer(cluster.getPeerCentroid(), cluster.getCentroid());
        List<BandPointDto> peerBand = prefer(cluster.getPeerBand(), cluster.getBand());
        return Feature2ExplainMetricsDto.PeerChartSummary.builder()
                .seriesPointCount(peerCentroid.size())
                .anchorReturnPct(returnPct(cluster.getAnchorSeries()))
                .industryIndexReturnPct(returnPct(cluster.getIndustryIndexSeries()))
                .peerCentroidReturnPct(returnPct(peerCentroid))
                .peerBandWidthLast(lastBandWidth(peerBand))
                .build();
    }

    private List<Feature2ExplainMetricsDto.PeerItemSummary> toPeerSummaries(List<PeerItemDto> peers, int limit) {
        if (peers == null || peers.isEmpty()) {
            return List.of();
        }
        return peers.stream()
                .limit(limit)
                .map(peer -> Feature2ExplainMetricsDto.PeerItemSummary.builder()
                        .stockCode(peer.getStockCode())
                        .companyName(peer.getCompanyName())
                        .corr(peer.getCorr())
                        .adjustedCorr(peer.getAdjustedCorr())
                        .corrStability(peer.getCorrStability())
                        .bestLag(peer.getBestLag())
                        .leadLagCorr(peer.getLeadLagCorr())
                        .lagConfidence(peer.getLagConfidence())
                        .relation(peer.getRelation() == null ? null : peer.getRelation().name())
                        .liquiditySimilarityScore(peer.getLiquiditySimilarityScore())
                        .volatilitySimilarityScore(peer.getVolatilitySimilarityScore())
                        .peerScore(peer.getPeerScore())
                        .displayStatus(peer.getDisplayStatus() == null ? null : peer.getDisplayStatus().name())
                        .build())
                .toList();
    }

    private List<Feature2ExplainMetricsDto.RecentNewsSummary> toRecentNews(List<NewsItemDto> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return List.of();
        }
        List<NewsItemDto> scoredNews = newsList.stream()
                .filter(news -> news != null && news.getSentimentScore() != null)
                .limit(NEWS_LIMIT)
                .toList();
        if (scoredNews.size() >= NEWS_LIMIT) {
            return toRecentNewsSummaries(scoredNews);
        }

        List<NewsItemDto> fallbackNews = newsList.stream()
                .filter(news -> news != null && news.getSentimentScore() == null)
                .limit(NEWS_LIMIT - scoredNews.size())
                .toList();

        List<NewsItemDto> selectedNews = new java.util.ArrayList<>(NEWS_LIMIT);
        selectedNews.addAll(scoredNews);
        selectedNews.addAll(fallbackNews);
        return toRecentNewsSummaries(selectedNews);
    }

    private List<Feature2ExplainMetricsDto.RecentNewsSummary> toRecentNewsSummaries(List<NewsItemDto> newsList) {
        return newsList.stream()
                .map(news -> Feature2ExplainMetricsDto.RecentNewsSummary.builder()
                        .newsId(news.getNewsId())
                        .title(news.getTitle())
                        .publisher(news.getPublisher())
                        .publishedAt(news.getPublishedAt() == null ? null : news.getPublishedAt().toString())
                        .sentimentScore(news.getSentimentScore())
                        .build())
                .toList();
    }

    private int countRelation(List<PeerItemDto> peers, PeerItemDto.Relation relation) {
        if (peers == null || relation == null) {
            return 0;
        }
        return (int) peers.stream()
                .filter(peer -> peer != null && relation.equals(peer.getRelation()))
                .count();
    }

    private int countDisplayStatus(List<PeerItemDto> peers, PeerItemDto.DisplayStatus status) {
        if (peers == null || status == null) {
            return 0;
        }
        return (int) peers.stream()
                .filter(peer -> peer != null && status.equals(peer.getDisplayStatus()))
                .count();
    }

    private <T> List<T> prefer(List<T> preferred, List<T> fallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return fallback == null ? List.of() : fallback;
    }

    private Double firstPct(List<RelativePointDto> series) {
        if (series == null || series.isEmpty() || series.get(0) == null) {
            return null;
        }
        return series.get(0).getPct();
    }

    private Double lastPct(List<RelativePointDto> series) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        RelativePointDto point = series.get(series.size() - 1);
        return point == null ? null : point.getPct();
    }

    private Double returnPct(List<RelativePointDto> series) {
        Double first = firstPct(series);
        Double last = lastPct(series);
        if (last == null) {
            return null;
        }
        return first == null ? last : last - first;
    }

    private Double lastBandWidth(List<BandPointDto> band) {
        if (band == null || band.isEmpty()) {
            return null;
        }
        BandPointDto point = band.get(band.size() - 1);
        if (point == null || point.getP20() == null || point.getP80() == null) {
            return null;
        }
        return point.getP80() - point.getP20();
    }
}
