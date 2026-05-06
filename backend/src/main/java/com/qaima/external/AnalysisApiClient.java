package com.qaima.external;

import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import com.qaima.dto.feature2.Feature2ExplainRequestDto;
import com.qaima.dto.feature2.Feature2ExplainResponseDto;
import com.qaima.dto.feature3.PortfolioAnalyzeRequestDto;
import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import reactor.core.publisher.Mono;

/**
 * FastAPI 분석 서버 호출용 클라이언트.
 * WebFlux 기반 → Mono로 비동기 응답 리턴.
 */
public interface AnalysisApiClient {

    Mono<FeatOneAnalysisResponseDto> requestStockAnalysis(FeatOneRequestDto request);

    Mono<Feature2ExplainResponseDto> requestFeature2Explain(Feature2ExplainRequestDto request);

    Mono<PortfolioAnalyzeResponseDto> requestPortfolioAnalysis(PortfolioAnalyzeRequestDto request);
}
