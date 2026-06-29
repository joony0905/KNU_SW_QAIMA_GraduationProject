package com.qaima.api.feat2;

import com.qaima.common.ApiResponse;
import com.qaima.dto.news.NewsDetailDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.service.feature2.NewsSentimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/feature2/news")
@RequiredArgsConstructor
@Tag(name = "Feature2")
@SecurityRequirements
public class Feature2NewsController {

    private final NewsSentimentService newsSentimentService;

    @GetMapping
    @Operation(summary = "List Feature2 news")
    public Mono<ApiResponse<List<NewsItemDto>>> getNewsList(@RequestParam String stockCode) {
        return newsSentimentService.loadNewsByStockCode(stockCode)
                .map(result -> ApiResponse.successWithWarnings(
                        result.getNewsList(),
                        result.getWarnings() == null ? List.of() : result.getWarnings()
                ));
    }

    @GetMapping("/{newsId}")
    @Operation(summary = "Get Feature2 news detail")
    public Mono<ApiResponse<NewsDetailDto>> getNewsDetail(@PathVariable Long newsId) {
        return newsSentimentService.loadNewsDetail(newsId)
                .map(detail -> ApiResponse.successWithWarnings(
                        detail,
                        detail.getWarnings() == null ? List.of() : detail.getWarnings()
                ));
    }
}
