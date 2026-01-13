package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.FinancialDto;
import com.qaima.mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAdminService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final FinancialMapper financialMapper;
    private final PlatformTransactionManager transactionManager;

    public Mono<FinancialDto> create(String stockCode, FinancialDto dto) {
        return Blocking.call(() -> tx().execute(status -> {
            Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode));

            if (dto.getYear() == null) throw new IllegalArgumentException("year 값은 필수입니다.");
            if (dto.getPeriodType() == null || dto.getPeriodType().isBlank())
                throw new IllegalArgumentException("periodType 값은 필수입니다. (A/Q/H/TTM)");

            Financial entity = new Financial();
            entity.setStock(stock);

            entity.setReportDate(dto.getReportDate() != null ? dto.getReportDate() : LocalDate.now());
            entity.setFiscalYear(dto.getYear());
            entity.setFiscalQuarter(dto.getQuarter());

            PeriodType pt = parsePeriodType(dto.getPeriodType());
            entity.setPeriodType(pt);

            applyPeriodFields(entity, dto, pt);

            entity.setRevenue(dto.getRevenue());
            entity.setOperatingIncome(dto.getOperatingIncome());
            entity.setNetIncome(dto.getNetIncome());
            entity.setAssets(dto.getAssets());
            entity.setLiabilities(dto.getLiabilities());
            entity.setEquity(dto.getEquity());
            entity.setCapitalStock(dto.getCapitalStock());
            entity.setMarketCap(dto.getMarketCap());

            Financial saved = financialRepository.save(entity);
            return financialMapper.toDto(saved);
        }));
    }

    public Mono<FinancialDto> update(Long financialId, FinancialDto dto) {
        return Blocking.call(() -> tx().execute(status -> {
            Financial entity = financialRepository.findById(financialId)
                    .orElseThrow(() -> new IllegalArgumentException("Financial not found: " + financialId));

            boolean periodRelatedChanged = false;

            if (dto.getReportDate() != null) { entity.setReportDate(dto.getReportDate()); periodRelatedChanged = true; }
            if (dto.getYear() != null) { entity.setFiscalYear(dto.getYear()); periodRelatedChanged = true; }
            if (dto.getQuarter() != null) { entity.setFiscalQuarter(dto.getQuarter()); periodRelatedChanged = true; }

            if (dto.getPeriodType() != null && !dto.getPeriodType().isBlank()) {
                PeriodType pt = parsePeriodType(dto.getPeriodType());
                entity.setPeriodType(pt);
                periodRelatedChanged = true;
            }

            if (periodRelatedChanged) {
                PeriodType pt = entity.getPeriodType();
                if (pt == null) throw new IllegalArgumentException("periodType 값은 필수입니다. (A/Q/H/TTM)");
                applyPeriodFields(entity, dto, pt);
            }

            if (dto.getRevenue() != null) entity.setRevenue(dto.getRevenue());
            if (dto.getOperatingIncome() != null) entity.setOperatingIncome(dto.getOperatingIncome());
            if (dto.getNetIncome() != null) entity.setNetIncome(dto.getNetIncome());
            if (dto.getAssets() != null) entity.setAssets(dto.getAssets());
            if (dto.getLiabilities() != null) entity.setLiabilities(dto.getLiabilities());
            if (dto.getEquity() != null) entity.setEquity(dto.getEquity());
            if (dto.getCapitalStock() != null) entity.setCapitalStock(dto.getCapitalStock());
            if (dto.getMarketCap() != null) entity.setMarketCap(dto.getMarketCap());

            Financial saved = financialRepository.save(entity);
            return financialMapper.toDto(saved);
        }));
    }

    public Mono<Void> delete(Long financialId) {
        return Blocking.run(() -> tx().executeWithoutResult(status -> {
            if (!financialRepository.existsById(financialId)) {
                log.warn("delete() called for non-existing financialId={}", financialId);
                return;
            }
            financialRepository.deleteById(financialId);
        }));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private PeriodType parsePeriodType(String raw) {
        String code = raw.trim().toUpperCase();
        try {
            return PeriodType.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("periodType은 A/Q/H/TTM 중 하나여야 합니다. (입력값: " + raw + ")");
        }
    }

    /**
     * entity.periodType 기준으로 periodNo/fiscalQuarter를 정규화해서 세팅
     * - Q: periodNo = quarter(1..4), fiscalQuarter 유지
     * - H: periodNo = half(1..2) (없으면 reportDate 월로 유추), fiscalQuarter = null
     * - A: periodNo = 1, fiscalQuarter = null
     * - TTM: periodNo = 0, fiscalQuarter = null
     */

    private void applyPeriodFields(Financial entity, FinancialDto dto, PeriodType pt) {
        switch (pt) {
            case Q -> {
                if (dto.getHalf() != null) throw new IllegalArgumentException("Q(분기) 입력에서는 half 값을 함께 보낼 수 없습니다.");
                Integer q = entity.getFiscalQuarter();
                if (q == null) q = dto.getQuarter();
                if (q == null) throw new IllegalArgumentException("Q(분기)인 경우 quarter 값이 필수입니다. (1~4)");
                if (q < 1 || q > 4) throw new IllegalArgumentException("quarter는 1~4만 허용됩니다.");
                entity.setFiscalQuarter(q);
                entity.setPeriodNo(q);
            }
            case H -> {
                if (dto.getQuarter() != null) throw new IllegalArgumentException("H(반기) 입력에서는 quarter 값을 함께 보낼 수 없습니다.");
                Integer h = dto.getHalf();
                if (h == null) {
                    LocalDate rd = entity.getReportDate();
                    int m = (rd != null) ? rd.getMonthValue() : LocalDate.now().getMonthValue();
                    h = (m <= 6) ? 1 : 2;
                }
                if (h < 1 || h > 2) throw new IllegalArgumentException("H(반기)인 경우 half는 1(H1) 또는 2(H2)만 허용됩니다.");
                entity.setFiscalQuarter(null);
                entity.setPeriodNo(h);
            }
            case A -> {
                if (dto.getQuarter() != null || dto.getHalf() != null)
                    throw new IllegalArgumentException("A(연간) 입력에서는 quarter/half 값을 함께 보낼 수 없습니다.");
                entity.setFiscalQuarter(null);
                entity.setPeriodNo(1);
            }
            case TTM -> {
                if (dto.getQuarter() != null || dto.getHalf() != null)
                    throw new IllegalArgumentException("TTM 입력에서는 quarter/half 값을 함께 보낼 수 없습니다.");
                entity.setFiscalQuarter(null);
                entity.setPeriodNo(0);
            }
        }
    }
}

