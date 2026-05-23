package com.qaima.service.candle;

import com.qaima.domain.Exchange;
import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.Stock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

public final class CandleTimePolicy {

    public static final ZoneId DEFAULT_TRADING_ZONE = ZoneId.of("Asia/Seoul");

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime LEGACY_UTC_MIDNIGHT_AS_KST_TIME = LocalTime.of(15, 0);

    private CandleTimePolicy() {
    }

    public static ZoneId tradingZone(Stock stock) {
        Exchange exchange = stock == null ? null : stock.getExchange();
        if (exchange != null && exchange.getTimezone() != null && !exchange.getTimezone().isBlank()) {
            try {
                return ZoneId.of(exchange.getTimezone().trim());
            } catch (Exception ignored) {
                // Fall back to exchange code mapping below.
            }
        }

        String code = exchange != null && exchange.getCode() != null
                ? exchange.getCode().trim().toUpperCase(Locale.ROOT)
                : "";

        return switch (code) {
            case "NASDAQ", "NYSE", "AMEX", "XNAS", "XNYS", "ARCX", "BATS" -> NEW_YORK;
            default -> DEFAULT_TRADING_ZONE;
        };
    }

    public static LocalDate tradingDate(OffsetDateTime ts, ZoneId tradingZone) {
        if (ts == null) {
            return null;
        }
        ZoneId zone = tradingZone == null ? DEFAULT_TRADING_ZONE : tradingZone;
        OffsetDateTime local = ts.atZoneSameInstant(zone).toOffsetDateTime();

        if (DEFAULT_TRADING_ZONE.equals(zone)
                && local.toLocalTime().equals(LEGACY_UTC_MIDNIGHT_AS_KST_TIME)) {
            return local.plusHours(9).toLocalDate();
        }

        return local.toLocalDate();
    }

    public static LocalDate tradingDate(PriceOhlcv candle, ZoneId tradingZone) {
        if (candle == null || candle.getId() == null || candle.getId().getTs() == null) {
            return null;
        }
        return tradingDate(candle.getId().getTs(), tradingZone);
    }

    public static OffsetDateTime canonicalTs(OffsetDateTime ts, Freq freq, ZoneId tradingZone) {
        if (ts == null || freq != Freq.ONE_D) {
            return ts;
        }
        ZoneId zone = tradingZone == null ? DEFAULT_TRADING_ZONE : tradingZone;
        LocalDate tradingDate = tradingDate(ts, zone);
        return tradingDate.atStartOfDay(zone).toOffsetDateTime();
    }

    public static String timezoneLabel(ZoneId zone) {
        return (zone == null ? DEFAULT_TRADING_ZONE : zone).getId();
    }
}
