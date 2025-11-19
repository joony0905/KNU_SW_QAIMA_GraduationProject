package com.qaima.dto;
import lombok.Builder;
import lombok.Data;

//application.yml에 snake_case로 json 자동변환하기때문에 erd내 변수명이랑 동일하게 json 보낼수있음
@Data
@Builder
public class StockDto {
    private Long stockId;               // 내부 고유 ID (PK)
    private String stockCode;           // 종목코드 (005930 / AAPL)
    private String isin;                // ISIN 코드
    private String companyName;         // 종목명

    // ERD: exchange_id bigint (FK)
    private Long exchangeId;            // 거래소 FK (내부값)

    // 외부 표시용 (KRX / NASDAQ 등)
    private String exchangeCode;

    // ERD: asset_type varchar(20)
    private String assetType;           // EQUITY/ETF/INDEX 등

    private String currency;            // KRW, USD 등

    // ERD: industry_id bigint (FK)
    private Long industryId;            // 산업군 FK

    /*
    ERD에는 industry code 없음 → 외부 API 제공 시 채울 수 있음
    kis나 stack에서 제공하면 erd 수정할게요
    private String industryCode;
    ** */

    private Double price;               // 현재가
    private Double changeRate;          // 등락률 (%)

    private String listedAt;            // yyyy-MM-dd
    private String delistedAt;          // yyyy-MM-dd or null

}
