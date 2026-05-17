package com.qaima.dto.sec;

public record SecCompanyTickerExchangeEntry(
        String cik,
        String companyName,
        String ticker,
        String exchange
) {
}
