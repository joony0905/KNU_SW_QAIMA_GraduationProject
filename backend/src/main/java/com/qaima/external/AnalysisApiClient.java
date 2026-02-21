package com.qaima.external;

import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.dto.FeatOneRequestDto;
import reactor.core.publisher.Mono;

/**
 * FastAPI 분석 서버 호출용 클라이언트.
 * WebFlux 기반 → Mono로 비동기 응답 리턴.
 */
public interface AnalysisApiClient {

    Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request);
}
