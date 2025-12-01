package com.qaima.external;

import com.qaima.dto.FeatOneRequestDto;
import com.qaima.dto.FeatOneResponseTextDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class FastApiAnalysisClient implements AnalysisApiClient {


    private final WebClient webClient;


    public FastApiAnalysisClient(
            @Qualifier("defaultWebClient") WebClient webClient
    ) {
        this.webClient = webClient;
    }

    @Value("${analysis.base-url}")
    private String baseUrl;

    @Override
    public FeatOneResponseTextDto requestStockAnalysis(FeatOneRequestDto request) {
        return webClient.post()
                .uri(baseUrl + "/api/v1/analysis/stock")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FeatOneResponseTextDto.class)
                .block(); // Webflux방식으로 리팩토링
    }
}
