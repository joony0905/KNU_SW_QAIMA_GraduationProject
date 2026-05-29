package com.qaima.api.stock;

import com.qaima.common.ApiResponse;
import com.qaima.dto.featuredstock.FeaturedStockDto;
import com.qaima.dto.featuredstock.FeaturedStockTopic;
import com.qaima.service.marketdata.reader.TopRankingReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/featured-stocks")
@RequiredArgsConstructor
@Tag(name = "Stock")
@SecurityRequirements
public class RankingStockController {

    private final TopRankingReader topRankingReader;

    @GetMapping
    @Operation(summary = "List featured stocks")
    public Mono<ApiResponse<List<FeaturedStockDto>>> getFeaturedStocks(
            @RequestParam FeaturedStockTopic topic,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return topRankingReader.fetch(topic, limit)
                .map(ApiResponse::success);
    }
}
