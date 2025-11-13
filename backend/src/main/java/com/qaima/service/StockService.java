package com.qaima.service;

import com.qaima.dto.StockDto;
import com.qaima.external.StockApiClient;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private final StockApiClient stockApiClient;
    // private final StockRepository stockRepository;  // DB붙일때 활성화 해주세요

    public StockService(StockApiClient stockApiClient) {
        this.stockApiClient = stockApiClient;
    }

    /**
     * 종목코드로 주식 정보를 조회한다.
     * 지금은 외부 API → DTO → 그대로 반환만 하고,
     * 나중에 stock 테이블이 실제로 붙으면 여기서 DB 캐시/저장을 끼워 넣는다.
     */

    public StockDto getStock(String stockCode) {
        // 1) 외부에서 가져오기
        StockDto dto = stockApiClient.fetchStock(stockCode);

        if (dto == null){
            return null;
        }

        // 2) TODO:여기서 DB 캐시 저장, exchange/industry FK 매핑 로직을 추가
        //    - 예: var cached = stockRepository.findByStockCode(stockCode);
        //    - 있으면 DB 엔티티 → DTO 변환해서 반환
        //    - 없으면 지금 dto 기준으로 stock 엔티티 만들어 저장
        //    - 외부에서 exchangeCode만 넘어오면 여기서 실제 exchange_id 조회해서 넣는 구조

        return dto;
    }
}

// entity 넣을때 주의점
// 0) 우선 DB 캐시 확인
// ex: var cached = stockRepository.findByStockCode(stockCode);
// if (cached != null) return mapper(cached);

// 1) 캐시 없으면 외부에서 가져오기 -> fetchStock
// 2) DB 저장
// external.exchangeCode → exchange 테이블에서 id 조회해서 set
// external.industryCode → industry 테이블에서 id 조회해서 set
// company_name, isin, asset_type, currency 등은 그대로 세팅
// 3) 저장한 엔티티를 다시 DTO로 변환해서 반환

