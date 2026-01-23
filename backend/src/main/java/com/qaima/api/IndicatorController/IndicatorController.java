package com.qaima.api.IndicatorController;

import com.qaima.domain.PeriodType;
import com.qaima.dto.IndicatorSnapshotDto;
import com.qaima.service.IndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stocks")
public class IndicatorController {

    private final IndicatorService indicatorService;

    @GetMapping("/{stockCode}/indicators")
    public Mono<IndicatorSnapshotDto> getIndicators(
            @PathVariable String stockCode,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestParam(name = "periodType", required = false) PeriodType periodType,
            @RequestParam(name = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return indicatorService.getIndicators(stockCode, exchange, periodType, asOfDate);
    }
}
