package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.BaseRate;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.baserate.BaseRateSyncService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaseRateFeatureService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BaseRateSyncService baseRateSyncService;

    public Mono<Feature2MetricsDto.BaseRateMetrics> loadLatest(Feature2MetaDto meta) {
        LocalDate today = LocalDate.now(SEOUL);

        return baseRateSyncService.ensureDailySynced(today)
                .map(this::toMetrics)
                .switchIfEmpty(Mono.defer(() -> {
                    meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                    return Mono.empty();
                }))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] baseRate load failed. cause={}", ex.getMessage());
                    meta.addWarning(Feat2WarningCode.BASE_RATE_LOAD_FAILED);
                    return baseRateSyncService.findLatest(today)
                            .map(this::toMetrics)
                            .switchIfEmpty(Mono.defer(() -> {
                                meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                                return Mono.empty();
                            }))
                            .cast(Feature2MetricsDto.BaseRateMetrics.class);
                });
    }

    private Feature2MetricsDto.BaseRateMetrics toMetrics(BaseRate baseRate) {
        return Feature2MetricsDto.BaseRateMetrics.builder()
                .date(baseRate.getBaseDate())
                .value(baseRate.getRateValue())
                .unit(baseRate.getUnitName())
                .build();
    }
}
