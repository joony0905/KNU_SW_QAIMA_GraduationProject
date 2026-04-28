package com.qaima.mapper;

import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.dto.candle.CandleDto;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CandleMapper {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private CandleMapper() {}

    /**
     * null-safe: 필수 값(ts, o/h/l/c) 누락이면 null 반환
     * - Feature2 정책(throw 금지 + warnings)과 호환
     */
    public static CandleDto toDtoOrNull(PriceOhlcv e) {
        if (e == null) return null;
        if (e.getId() == null || e.getId().getTs() == null) return null;

        if (e.getOpen() == null || e.getHigh() == null || e.getLow() == null || e.getClose() == null) {
            return null;
        }

        OffsetDateTime normalizedTs = normalizeTsForOutput(e);
        long t = normalizedTs.toEpochSecond();
        long v = (e.getVolume() == null)
                ? 0L
                : e.getVolume().setScale(0, RoundingMode.DOWN).longValue();

        return new CandleDto(
                t,
                e.getOpen().doubleValue(),
                e.getHigh().doubleValue(),
                e.getLow().doubleValue(),
                e.getClose().doubleValue(),
                v
        );
    }

    /**
     * Feature1 호환을 위해 "그대로 두되" 내부에서 null이면 null pointer 대신 명확히 터지게 하지 않음.
     * -> Feature1이 이걸 직접 쓰면 컴파일은 되지만 NPE 대신 null이 내려갈 수 있으니,
     * Feature1 호출부는 toSeries를 쓰도록 유지하는 게 안전.
     */
    public static CandleDto toDto(PriceOhlcv e) {
        CandleDto dto = toDtoOrNull(e);
        if (dto == null) {
            throw new IllegalArgumentException("PriceOhlcv has null required fields for CandleDto");
        }
        return dto;
    }

    /**
     * series 변환: null row는 자동 제거하고, 일봉은 KST 거래일 기준으로 중복 제거
     */
    public static List<CandleDto> toSeries(List<PriceOhlcv> list) {
        if (list == null || list.isEmpty()) return List.of();

        Map<String, PriceOhlcv> deduped = new LinkedHashMap<>();
        list.stream()
                .filter(entity -> entity != null && entity.getId() != null)
                .sorted(Comparator.comparing(entity -> entity.getId().getTs()))
                .forEach(entity -> deduped.put(logicalKey(entity), entity));

        return deduped.values().stream()
                .map(CandleMapper::toDtoOrNull)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(CandleDto::getT))
                .toList();
    }

    private static OffsetDateTime normalizeTsForOutput(PriceOhlcv entity) {
        OffsetDateTime ts = entity.getId().getTs();
        if (entity.getId().getFreq() != Freq.ONE_D) return ts;

        LocalDate tradingDay = ts.atZoneSameInstant(KST).toLocalDate();
        return tradingDay.atStartOfDay(KST).toOffsetDateTime();
    }

    private static String logicalKey(PriceOhlcv entity) {
        if (entity.getId().getFreq() == Freq.ONE_D) {
            LocalDate tradingDay = entity.getId().getTs().atZoneSameInstant(KST).toLocalDate();
            return entity.getId().getStockId() + "|" + entity.getId().getFreq() + "|" + tradingDay;
        }

        return entity.getId().getStockId() + "|" + entity.getId().getFreq() + "|" + entity.getId().getTs().toInstant();
    }
}
