package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.repository.FinancialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final FinancialRepository financialRepository;
    private final PlatformTransactionManager transactionManager;


    /**
     * 한국투자증권 주식현재가(KisStatResponse.Output)를 기반으로
     * 해당 종목의 TTM 스냅샷을 financial에 upsert.
     *
     * - 기준일(asOfDate) 기준 fiscalYear 설정
     * - 스냅샷 특성상 fiscalQuarter는 null 유지(호환용)
     * - periodType = TTM 고정
     * - periodNo = 0 고정 (새 유니크 키의 핵심)
     */

    public Mono<Financial> upsertKisSnapshot(
            Stock stock,
            KisStatResponseDto.Output output,
            LocalDate asOfDate
    ) {
        return Blocking.call(() -> tx().execute(status -> {
            LocalDate baseDate = (asOfDate != null ? asOfDate : LocalDate.now());
            int fiscalYear = baseDate.getYear();

            PeriodType periodType = PeriodType.TTM;
            int periodNo = 0;

            Financial financial = financialRepository
                    .findByStockAndFiscalYearAndPeriodNoAndPeriodType(stock, fiscalYear, periodNo, periodType)
                    .orElseGet(Financial::new);

            financial.setStock(stock);
            financial.setReportDate(baseDate);
            financial.setFiscalYear(fiscalYear);
            financial.setPeriodType(periodType);
            financial.setPeriodNo(periodNo);
            financial.setFiscalQuarter(null);
            financial.setVersion(1);

            financial.setCurrency(stock.getCurrency() != null ? stock.getCurrency() : "KRW");
            financial.setSource("KIS_INQUIRE_PRICE");

            if (output.getLstn_stcn() != null && !output.getLstn_stcn().isBlank()) {
                try { financial.setCapitalStock(new BigDecimal(output.getLstn_stcn())); }
                catch (NumberFormatException e) { log.warn("[FinancialService] lstn_stcn 파싱 실패: {}", output.getLstn_stcn(), e); }
            }

            if (output.getHts_avls() != null && !output.getHts_avls().isBlank()) {
                try { financial.setMarketCap(new BigDecimal(output.getHts_avls())); }
                catch (NumberFormatException e) { log.warn("[FinancialService] hts_avls 파싱 실패: {}", output.getHts_avls(), e); }
            }

            if (output.getPer() != null && !output.getPer().isBlank()) {
                try { financial.setPer(new BigDecimal(output.getPer())); }
                catch (NumberFormatException e) { log.warn("[FinancialService] per 파싱 실패: {}", output.getPer(), e); }
            }

            if (output.getPbr() != null && !output.getPbr().isBlank()) {
                try { financial.setPbr(new BigDecimal(output.getPbr())); }
                catch (NumberFormatException e) { log.warn("[FinancialService] pbr 파싱 실패: {}", output.getPbr(), e); }
            }

            Financial saved = financialRepository.save(financial);

            log.info("[FinancialService] KIS TTM 스냅샷 upsert 완료: stockCode={}, fiscalYear={}, periodType={}, periodNo={}, id={}",
                    stock.getStockCode(), fiscalYear, periodType, periodNo, saved.getFinancialId());

            return saved;
        }));
    }

    public Mono<List<Financial>> getLastNYearsAnnualByStock(Stock stock, int years, LocalDate asOfDate) {
        return Blocking.call(() -> {
            int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
            int fromYear = toYear - (years - 1);

            return financialRepository
                    .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                            stock, PeriodType.A, fromYear, toYear
                    );
        });
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}


