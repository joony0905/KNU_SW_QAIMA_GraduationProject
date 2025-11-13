package com.qaima.external;

import com.qaima.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;


@RequiredArgsConstructor
@Component
public class StockApiClient {

    private final WebClient webClient;

    /**
     * 외부 주식 API에서 종목 정보를 가져온다.
     * 실제 필드는 외부 응답에 맞춰서 매핑해야 한다.
     */

    // 외부 API 응답 구조에 맞게 수정
    // ex: GET https://api.example.com/stocks/{code}
    // -> 응답용 DTO 하나 더 만들어서 StockDto로 변환하는 단계 추가 필요
    public StockDto fetchStock(String stockCode) {
        try {
            ExternalStockResponse external = webClient.get()
                    .uri("https://api.example.com/stocks/{code}", stockCode)
                    .retrieve()
                    .bodyToMono(ExternalStockResponse.class)
                    .block();  // 지금은 단순화를 위해 block

            if (external == null) {
                return null;
            }

            // 외부 필드명을 우리 필드명으로 맞추는 자리
            return StockDto.builder()
                    .stockCode(external.getSymbol())            // 외부 symbol → 우리 stock_code
                    .isin(external.getIsin())                  // ISIN 코드
                    .companyName(external.getCompanyName())    // 회사명
                    .exchangeCode(external.getExchangeCode())  // 거래소 (KRX, NASDAQ 등)
                    .assetType(external.getAssetType())        // 주식/ETF 등
                    .currency(external.getCurrency())          // 통화단위 (KRW, USD 등)
                    .industryCode(external.getIndustryCode())  // 산업군 코드
                    .price(external.getPrice())                // 현재가
                    .changeRate(external.getChangeRate())      // 전일대비 등락률
                    .listedAt(external.getListedAt())          // 상장일
                    .delistedAt(external.getDelistedAt())      // 상폐일
                    .build();

        } catch (Exception e) {
            //TODO: 여기서 바로 예외처리 || null 리턴 후 service에서 처리
            return null;
        }
    }
        /**
         * 외부 응답을 담는 임시 DTO
         * 실제 필드명은 외부 API 스펙에 맞춰 수정
         */
        @lombok.Data
        private static class ExternalStockResponse {
            private String symbol;         // 예: 005930
            private String isin;           // 국제증권식별번호
            private String companyName;    // 삼성전자
            private String exchangeCode;   // KRX, NASDAQ 등
            private String assetType;      // EQUITY, ETF 등
            private String currency;       // KRW, USD 등
            private String industryCode;   // IT_HARDWARE 등
            private Double price;          // 현재가
            private Double changeRate;     // 전일대비 등락률
            private String listedAt;       // yyyy-MM-dd
            private String delistedAt;     // yyyy-MM-dd or null
        }

    }

