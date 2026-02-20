package com.qaima.api.MarketSnapshotAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.MarketSnapshotBackfillResult;
import com.qaima.service.MarketSnapshotBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/market-snapshots")
public class MarketSnapshotAdminController {

    private final MarketSnapshotBackfillService backfillService;

    /**
     * 관리자 전용 백필(단건)
     * - asOfDate는 오늘만 허용 (KIS 실시간성 정책)
     * - KIS 호출량 관리 필요: 연속 호출 시 delayMs 또는 개별 호출 권장
     *
     * 테스트 예시:
     * POST /api/v1/admin/market-snapshots/backfill?stockCode=005930&exchange=KOSPI
     */
    @PostMapping("/backfill")
    public Mono<ApiResponse<MarketSnapshotBackfillResult>> backfillOne(
            @RequestParam String stockCode,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return backfillService.backfillOne(stockCode, exchange, asOfDate, force)
                .map(ApiResponse::success);
    }

    /**
     * 관리자 전용 백필(누락/불완전 스냅샷만)
     * - 이미 채워진 스냅샷은 스킵
     * - delayMs로 KIS 호출 간격 조절 (실전 20 rps 제한 고려)
     *
     * 테스트 예시:
     * POST /api/v1/admin/market-snapshots/backfill/missing?exchange=KOSPI&limit=30&delayMs=120
     */
    @PostMapping("/backfill/missing")
    public Mono<ApiResponse<List<MarketSnapshotBackfillResult>>> backfillMissing(
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long delayMs,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return backfillService.backfillMissing(exchange, limit, delayMs, asOfDate)
                .map(ApiResponse::success);
    }
}
