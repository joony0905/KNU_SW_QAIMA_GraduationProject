package com.qaima.external;

import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.dto.FeatOneRequestDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class FastApiAnalysisClient implements AnalysisApiClient {

    private final WebClient webClient;

    public FastApiAnalysisClient(@Qualifier("analysisWebClient") WebClient webClient) {
        this.webClient = webClient;
    }
    @Override
    public Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request) {
        return webClient.post()
                .uri("/api/v1/analysis/feature1")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FeatOneAnalysisResponseDto.class);
        //.subscribeOn(Schedulers.boundedElastic()) 블로킹있을시
    }
}
