package com.qaima.external;

import com.qaima.dto.FeatOneRequestDto;
import com.qaima.dto.FeatOneResponseTextDto;

/**
 * FastAPI 분석 서버 호출용 클라이언트.
 * - 실제 구현에서는 WebClient / RestTemplate 등을 사용해서 HTTP POST 호출.
 */
public interface AnalysisApiClient {

    FeatOneResponseTextDto requestStockAnalysis(FeatOneRequestDto request);
}
