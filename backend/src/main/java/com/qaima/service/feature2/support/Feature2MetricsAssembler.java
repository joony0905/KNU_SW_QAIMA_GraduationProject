package com.qaima.service.feature2.support;

import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import com.qaima.service.feature2.model.Feature2StockContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Feature2MetricsAssembler {

    public Feature2MetricsDto empty() {
        return Feature2MetricsDto.empty();
    }

    public void attachStock(Feature2MetricsDto metrics, Feature2StockContext stockContext) {
        if (metrics == null || stockContext == null) {
            return;
        }
        metrics.setStock(stockContext.stockMeta());
    }

    public void attachIndustry(Feature2MetricsDto metrics, Feature2IndustryContext industryContext) {
        if (metrics == null || industryContext == null) {
            return;
        }
        metrics.setIndustry(industryContext.industryMeta());
    }

    public void attachBaseRate(Feature2MetricsDto metrics, Feature2MetricsDto.BaseRateMetrics baseRate) {
        if (metrics == null || baseRate == null) {
            return;
        }
        metrics.setBaseRate(baseRate);
    }

    public void attachShortSelling(Feature2MetricsDto metrics, Feature2MetricsDto.ShortSellingMetrics shortSelling) {
        if (metrics == null || shortSelling == null) {
            return;
        }
        metrics.setShortSelling(shortSelling);
    }

    public void attachIndustryIndex(Feature2MetricsDto metrics, IndustryIndexBlockDto industryIndex) {
        if (metrics == null || industryIndex == null) {
            return;
        }
        metrics.setIndustryIndex(industryIndex);
    }

    public void attachPeerCluster(Feature2MetricsDto metrics, PeerClusterDto peerCluster) {
        if (metrics == null) {
            return;
        }
        metrics.setPeerCluster(peerCluster);
    }

    public void attachNewsList(Feature2MetricsDto metrics, List<NewsItemDto> newsList) {
        if (metrics == null) {
            return;
        }
        metrics.setNewsList(newsList);
    }

    public void attachNewsSentimentSummary(
            Feature2MetricsDto metrics,
            Feature2MetricsDto.NewsSentimentSummary newsSentimentSummary
    ) {
        if (metrics == null) {
            return;
        }
        metrics.setNewsSentimentSummary(newsSentimentSummary);
    }
}
