package com.qaima.service;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.FinancialDto;
import com.qaima.Mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAdminService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final FinancialMapper financialMapper;

    @Transactional
    public FinancialDto create(String stockCode, FinancialDto dto) {
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode));

        if (dto.getYear() == null) {
            throw new IllegalArgumentException("year 값은 필수입니다.");
        }
        if (dto.getPeriodType() == null || dto.getPeriodType().isBlank()) {
            throw new IllegalArgumentException("periodType 값은 필수입니다. (A/Q/TTM)");
        }

        log.info("CREATE Financial for stock={}, year={}, quarter={}, periodType={}",
                stockCode, dto.getYear(), dto.getQuarter(), dto.getPeriodType());

        Financial entity = new Financial();
        entity.setStock(stock);

        if (dto.getReportDate() != null) {
            entity.setReportDate(dto.getReportDate());
        } else {
            entity.setReportDate(LocalDate.now());
        }

        // 회계 기간 정보 매핑
        entity.setFiscalYear(dto.getYear());
        entity.setFiscalQuarter(dto.getQuarter());

        String code = dto.getPeriodType().trim().toUpperCase(); // "A", "Q", "TTM"
        try {
            entity.setPeriodType(PeriodType.valueOf(code));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("periodType은 A/Q/TTM 중 하나여야 합니다. (입력값: " + dto.getPeriodType() + ")");
        }

        // 금액 지표 매핑
        entity.setRevenue(dto.getRevenue());
        entity.setOperatingIncome(dto.getOperatingIncome());
        entity.setNetIncome(dto.getNetIncome());
        entity.setAssets(dto.getAssets());
        entity.setLiabilities(dto.getLiabilities());
        entity.setEquity(dto.getEquity());
        entity.setCapitalStock(dto.getCapitalStock());
        entity.setMarketCap(dto.getMarketCap());

        // 비율들(operatingMargin, netMargin, roe 등)은
        // 필요하면 여기서 계산해서 넣거나, KIS / CSV에서 오는 값을 사용

        log.info("Before save: periodType={}, fiscalYear={}, fiscalQuarter={}",
                entity.getPeriodType(), entity.getFiscalYear(), entity.getFiscalQuarter());
        log.info("RAW DTO in create(): year={}, quarter={}, periodType={}",
                dto.getYear(), dto.getQuarter(), dto.getPeriodType());

        Financial saved = financialRepository.save(entity);

        return financialMapper.toDto(saved);
    }

    @Transactional
    public FinancialDto update(Long financialId, FinancialDto dto) {
        Financial entity = financialRepository.findById(financialId)
                .orElseThrow(() -> new IllegalArgumentException("Financial not found: " + financialId));

        if (dto.getReportDate() != null) {
            entity.setReportDate(dto.getReportDate());
        }

        if (dto.getYear() != null) {
            entity.setFiscalYear(dto.getYear());
        }
        if (dto.getQuarter() != null) {
            entity.setFiscalQuarter(dto.getQuarter());
        }
        if (dto.getPeriodType() != null && !dto.getPeriodType().isBlank()) {
            String code = dto.getPeriodType().trim().toUpperCase();
            try {
                entity.setPeriodType(PeriodType.valueOf(code));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("periodType은 A/Q/TTM 중 하나여야 합니다. (입력값: " + dto.getPeriodType() + ")");
            }
        }

        if (dto.getRevenue() != null) {
            entity.setRevenue(dto.getRevenue());
        }
        if (dto.getOperatingIncome() != null) {
            entity.setOperatingIncome(dto.getOperatingIncome());
        }
        if (dto.getNetIncome() != null) {
            entity.setNetIncome(dto.getNetIncome());
        }
        if (dto.getAssets() != null) {
            entity.setAssets(dto.getAssets());
        }
        if (dto.getLiabilities() != null) {
            entity.setLiabilities(dto.getLiabilities());
        }
        if (dto.getEquity() != null) {
            entity.setEquity(dto.getEquity());
        }
        if (dto.getCapitalStock() != null) {
            entity.setCapitalStock(dto.getCapitalStock());
        }
        if (dto.getMarketCap() != null) {
            entity.setMarketCap(dto.getMarketCap());
        }

        log.info("UPDATE Financial id={}, periodType={}, fiscalYear={}, fiscalQuarter={}",
                financialId, entity.getPeriodType(), entity.getFiscalYear(), entity.getFiscalQuarter());

        Financial saved = financialRepository.save(entity);
        return financialMapper.toDto(saved);
    }

    @Transactional
    public void delete(Long financialId) {
        if (!financialRepository.existsById(financialId)) {
            log.warn("delete() called for non-existing financialId={}", financialId);
            return;
        }
        financialRepository.deleteById(financialId);
    }
}
