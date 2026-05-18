package com.qaima.repository;

import com.qaima.domain.Sec13fHolding;
import com.qaima.domain.Stock;
import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface Sec13fHoldingRepository extends JpaRepository<Sec13fHolding, Long> {

    @EntityGraph(attributePaths = {"stock", "filing"})
    Optional<Sec13fHolding> findByAccessionNumberAndStockAndCusip(
            String accessionNumber,
            Stock stock,
            String cusip
    );

    @EntityGraph(attributePaths = {"stock", "filing"})
    List<Sec13fHolding> findByAccessionNumberIn(Collection<String> accessionNumbers);

    @EntityGraph(attributePaths = {"stock", "filing"})
    List<Sec13fHolding> findByStockAndReportPeriodOrderByManagerCikAscFilingDateDescAccessionNumberDesc(
            Stock stock,
            LocalDate reportPeriod
    );

    @Query("""
        select distinct h.stock
        from Sec13fHolding h
        where h.managerCik = :managerCik
            and h.reportPeriod = :reportPeriod
    """)
    List<Stock> findDistinctStocksByManagerCikAndReportPeriod(
            @Param("managerCik") String managerCik,
            @Param("reportPeriod") LocalDate reportPeriod
    );

    @Query("""
        select distinct h.reportPeriod
        from Sec13fHolding h
        where h.stock = :stock
        order by h.reportPeriod asc
    """)
    List<LocalDate> findDistinctReportPeriodsByStock(@Param("stock") Stock stock);

    @Query(value = """
        SELECT
            latest.stock_id AS stockId,
            latest.report_period AS reportPeriod,
            SUBSTRING_INDEX(GROUP_CONCAT(latest.cusip ORDER BY latest.manager_cik ASC, latest.cusip ASC SEPARATOR ','), ',', 1) AS cusip,
            COUNT(DISTINCT latest.manager_cik) AS institutionCount,
            COALESCE(SUM(latest.filing_row_count), 0) AS filingRowCount,
            COALESCE(SUM(latest.shares), 0) AS sharesHeld,
            COALESCE(SUM(latest.market_value_usd), 0) AS marketValueUsd
        FROM (
            SELECT h.*
            FROM sec_13f_holding h
            JOIN (
                SELECT ranked_filing.*
                FROM (
                    SELECT
                        filing_candidate.*,
                        ROW_NUMBER() OVER (
                            PARTITION BY filing_candidate.stock_id, filing_candidate.report_period, filing_candidate.manager_cik
                            ORDER BY filing_candidate.filing_date DESC, filing_candidate.accession_number DESC
                        ) AS rn
                    FROM (
                        SELECT DISTINCT
                            h.stock_id,
                            h.report_period,
                            h.manager_cik,
                            h.accession_number,
                            h.filing_date
                        FROM sec_13f_holding h
                        WHERE h.stock_id IN (:stockIds)
                            AND h.report_period IN (:reportPeriods)
                            AND NOT EXISTS (
                                SELECT 1
                                FROM sec_13f_filing rf
                                WHERE rf.manager_cik = h.manager_cik
                                    AND rf.report_period = h.report_period
                                    AND rf.is_amendment = TRUE
                                    AND UPPER(COALESCE(rf.amendment_type, '')) LIKE '%RESTAT%'
                                    AND (
                                        rf.filing_date > h.filing_date
                                        OR (
                                            rf.filing_date = h.filing_date
                                            AND rf.accession_number > h.accession_number
                                        )
                                    )
                            )
                    ) filing_candidate
                ) ranked_filing
                WHERE ranked_filing.rn = 1
            ) latest_filing
                ON latest_filing.stock_id = h.stock_id
                AND latest_filing.report_period = h.report_period
                AND latest_filing.manager_cik = h.manager_cik
                AND latest_filing.accession_number = h.accession_number
        ) latest
        GROUP BY latest.stock_id, latest.report_period
        ORDER BY latest.stock_id ASC, latest.report_period ASC
        """, nativeQuery = true)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "60000"))
    List<AggregatePeriodProjection> aggregateLatestByStockIdsAndReportPeriods(
            @Param("stockIds") Collection<Long> stockIds,
            @Param("reportPeriods") Collection<LocalDate> reportPeriods
    );

    @Query(value = """
        SELECT
            h.stock_id AS stockId,
            h.report_period AS reportPeriod
        FROM sec_13f_holding h
        GROUP BY h.stock_id, h.report_period
        ORDER BY h.stock_id ASC, h.report_period ASC
        """, nativeQuery = true)
    List<StockPeriodProjection> findDistinctStockPeriods();

    interface AggregatePeriodProjection {
        Long getStockId();

        LocalDate getReportPeriod();

        String getCusip();

        Integer getInstitutionCount();

        Integer getFilingRowCount();

        BigDecimal getSharesHeld();

        BigDecimal getMarketValueUsd();
    }

    interface StockPeriodProjection {
        Long getStockId();

        LocalDate getReportPeriod();
    }
}
