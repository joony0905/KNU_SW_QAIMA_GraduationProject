package com.qaima.external;

import com.qaima.dto.FeatOneRequestDto;
import com.qaima.dto.FeatOneResponseTextDto;
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
    public Mono<FeatOneResponseTextDto> requestStockAnalysis(FeatOneRequestDto request) {
        return webClient.post()
                .uri("/api/v1/analysis/stock")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FeatOneResponseTextDto.class);
        //.subscribeOn(Schedulers.boundedElastic()) 블로킹있을시
    }
}
