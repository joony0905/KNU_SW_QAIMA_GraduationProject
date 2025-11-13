package com.qaima.dto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockDto {
    private String stockCode;       // 종목코드 (예: 005930)
    private String isin;            // 국제증권식별번호
    private String companyName;     // 종목명 (삼성전자 등)
    private String exchangeCode;    // 거래소 코드 (KRX, NASDAQ 등)
    private String assetType;       // 주식, ETF, ETN 등
    private String currency;        // KRW, USD 등
    private String industryCode;    // 산업군 코드 (industry 테이블 FK 대신 코드만)
    private Double price;           // 현재가
    private Double changeRate;      // 전일대비 등락률 (%)
    private String listedAt;        // 상장일 (yyyy-MM-dd 문자열)
    private String delistedAt;      // 상폐일 (nullable)
}
