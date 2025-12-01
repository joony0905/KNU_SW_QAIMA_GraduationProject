package com.qaima.service;

import com.qaima.domain.Financial;
import com.qaima.domain.Freq;
import com.qaima.domain.IndicatorValue;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.Stock;
import com.qaima.dto.*;
import com.qaima.external.AnalysisApiClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.IndicatorValueRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private final StockRepository stockRepository;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final IndicatorValueRepository indicatorValueRepository;
    private final FinancialRepository financialRepository;
    private final AnalysisApiClient analysisApiClient;

    public FeatOneResponseDataDto getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목 코드: " + stockCode));

        // 1) 캔들 조회
        List<PriceOhlcv> candles = priceOhlcvRepository
                .findByStockCodeAndFreqAndTsBetween(stockCode, freq, from, to);

        // 2) 지표 조회 (MVP에서는 exist 안 하면 빈 리스트)
        List<IndicatorValue> indicators = indicatorValueRepository
                .findByStockAndFreqAndTsBetweenOrderByTs(stock, freq, from, to);

        // 3) 재무제표 최근 N개
        List<Financial> financials = financialRepository
                .findTopByStockOrderByReportDateDescVersionDesc(stock);

        // 4) DTO 매핑
        List<PriceOhlcvDto> candleDtos = candles.stream()
                .map(this::toPriceOhlcvDto)
                .collect(toList());

        List<IndicatorValueDto> indicatorDtos = indicators.stream()
                .map(this::toIndicatorValueDto)
                .collect(toList());

        List<FinancialSummaryDto> financialDtos = financials.stream()
                .map(this::toFinancialSummaryDto)
                .collect(toList());

        // 5) FastAPI 분석 호출
        FeatOneRequestDto analysisRequest = FeatOneRequestDto.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .build();

        String analysisText = null;
        try {
            FeatOneResponseTextDto analysisResponse =
                    analysisApiClient.requestStockAnalysis(analysisRequest);
            analysisText = analysisResponse != null ? analysisResponse.getAnalysisText() : null;
        } catch (Exception ex) {
            // 실패해도 기능1 기본데이터는 응답 – 로그만 남기고 무시
            // log.warn("FastAPI 분석 호출 실패", ex);
        }

        return FeatOneResponseDataDto.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .exchangeCode(stock.getExchange().getCode())
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .analysisText(analysisText)
                .build();
    }

    // ======== private mapper 메서드들 ========

    private PriceOhlcvDto toPriceOhlcvDto(PriceOhlcv entity) {
        return PriceOhlcvDto.builder()
                .ts(entity.getId().getTs())
                .freq(entity.getId().getFreq())
                .open(entity.getOpen())
                .high(entity.getHigh())
                .low(entity.getLow())
                .close(entity.getClose())
                .volume(entity.getVolume())
                .build();
    }

    private IndicatorValueDto toIndicatorValueDto(IndicatorValue entity) {
        return IndicatorValueDto.builder()
                .ts(entity.getTs())
                .freq(entity.getFreq())
                .key(entity.getKey())
                .valueNum(entity.getValueNum())
                .valueJson(entity.getValueJson())
                .build();
    }

    private FinancialSummaryDto toFinancialSummaryDto(Financial f) {
        return FinancialSummaryDto.builder()
                .fiscalYear(f.getFiscalYear())
                .fiscalQuarter(f.getFiscalQuarter())
                .periodType(f.getPeriodType().name())
                .reportDate(f.getReportDate())
                .revenue(f.getRevenue())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .equity(f.getEquity())
                .liabilities(f.getLiabilities())
                .roe(f.getRoe())
                .per(f.getPer())
                .pbr(f.getPbr())
                .build();
    }
}
