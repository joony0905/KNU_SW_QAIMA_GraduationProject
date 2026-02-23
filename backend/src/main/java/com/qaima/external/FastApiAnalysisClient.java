package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.dto.FeatOneRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class FastApiAnalysisClient implements AnalysisApiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FastApiAnalysisClient(
            @Qualifier("analysisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request) {

        return webClient.post()
                .uri("/api/v1/analysis/feature1")
                .bodyValue(request)
                .exchangeToMono(resp -> {
                    HttpStatusCode status = resp.statusCode();

                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                if (status.isError()) {
                                    log.error("[FastAPI] status={} body={}", status.value(), body);
                                    return Mono.error(new RuntimeException("FASTAPI_HTTP_" + status.value()));
                                }

                                try {
                                    FeatOneAnalysisResponseDto dto =
                                            objectMapper.readValue(body, FeatOneAnalysisResponseDto.class);
                                    return Mono.just(dto);
                                } catch (Exception e) {
                                    log.error("[FastAPI] decode failed. body={}", body, e);
                                    return Mono.error(e);
                                }
                            });
                });
    }
}