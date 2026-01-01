package com.qaima.service;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.repository.FinancialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final FinancialRepository financialRepository;

    /**
     * 한국투자증권 주식현재가(KisStatResponse.Output)를 기반으로
     * 해당 종목의 TTM 스냅샷을 financial에 upsert.
     *
     * - 기준일(asOfDate) 기준 fiscalYear 설정
     * - 분기 정보는 스냅샷 특성상 null
     * - periodType = TTM 고정
     */
    @Transactional
    public Financial upsertKisSnapshot(
            Stock stock,
            KisStatResponseDto.Output output,
            LocalDate asOfDate
    ) {
        int fiscalYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
        Integer fiscalQuarter = null;              // 스냅샷이므로 분기는 해당사항없음
        PeriodType periodType = PeriodType.TTM;    // TTM 스냅샷

        // 동일 (stock, fiscalYear, fiscalQuarter, periodType)에 대한 기존 레코드 조회
        Financial financial = financialRepository
                .findByStockAndFiscalYearAndFiscalQuarterAndPeriodType(
                        stock, fiscalYear, fiscalQuarter, periodType
                )
                .orElseGet(Financial::new);

        financial.setStock(stock);
        financial.setReportDate(asOfDate != null ? asOfDate : LocalDate.now());
        financial.setFiscalYear(fiscalYear);
        financial.setFiscalQuarter(fiscalQuarter);
        financial.setPeriodType(periodType);
        financial.setVersion(1);

        // 통화 / 소스
        financial.setCurrency(
                stock.getCurrency() != null ? stock.getCurrency() : "KRW"
        );
        financial.setSource("KIS_INQUIRE_PRICE");

        // ------------ KIS 주식현재가에서 가져올 수 있는 값들 ------------

        // 상장주식수 lstn_stcn
        if (output.getLstn_stcn() != null && !output.getLstn_stcn().isBlank()) {
            try {
                financial.setCapitalStock(new BigDecimal(output.getLstn_stcn()));
            } catch (NumberFormatException e) {
                log.warn("[FinancialService] lstn_stcn 파싱 실패: {}", output.getLstn_stcn(), e);
            }
        }

        // 시가총액 hts_avls (단위 확인 후 필요시 보정)
        if (output.getHts_avls() != null && !output.getHts_avls().isBlank()) {
            try {
                financial.setMarketCap(new BigDecimal(output.getHts_avls()));
            } catch (NumberFormatException e) {
                log.warn("[FinancialService] hts_avls 파싱 실패: {}", output.getHts_avls(), e);
            }
        }

        // PER
        if (output.getPer() != null && !output.getPer().isBlank()) {
            try {
                financial.setPer(new BigDecimal(output.getPer()));
            } catch (NumberFormatException e) {
                log.warn("[FinancialService] per 파싱 실패: {}", output.getPer(), e);
            }
        }

        // PBR
        if (output.getPbr() != null && !output.getPbr().isBlank()) {
            try {
                financial.setPbr(new BigDecimal(output.getPbr()));
            } catch (NumberFormatException e) {
                log.warn("[FinancialService] pbr 파싱 실패: {}", output.getPbr(), e);
            }
        }

        // 나머지 revenue / netIncome / assets 등은
        // 별도 재무 API 연동 시 채우거나,
        // 분석 서버에서 계산된 값을 가져옴
        // 따라서 추후 수정이 필요함

        Financial saved = financialRepository.save(financial);

        log.info("[FinancialService] KIS TTM 스냅샷 upsert 완료: stockCode={}, fiscalYear={}, periodType={}, id={}",
                stock.getStockCode(), fiscalYear, periodType, saved.getFinancialId());

        return saved;
    }

    /**
     * 5개년 "연간(Annual)" 재무제표 조회
     * 기준 연도에서 과거 N년까지 조회, 최신 연도/분기 순 정렬
     */
    @Transactional(readOnly = true)
    public List<Financial> getLastNYearsAnnualByStock(Stock stock, int years, LocalDate asOfDate) {
        int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
        int fromYear = toYear - (years - 1);

        return financialRepository
                .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescFiscalQuarterDesc(
                        stock,
                        PeriodType.A,
                        fromYear,
                        toYear
                );
    }
}
