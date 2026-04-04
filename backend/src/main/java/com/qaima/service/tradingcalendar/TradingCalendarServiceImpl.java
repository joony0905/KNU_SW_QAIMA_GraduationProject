package com.qaima.service.tradingcalendar;

import com.qaima.domain.MarketHoliday;
import com.qaima.repository.MarketHolidayRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingCalendarServiceImpl implements TradingCalendarService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MARKET_OPEN_HOUR = 9;

    private final MarketHolidayRepository marketHolidayRepository;

    private final Map<String, Set<LocalDate>> yearlyHolidayCache = new HashMap<>();

    @Override
    public boolean isTradingDay(LocalDate date, String market) {
        if (date == null) {
            return false;
        }

        return !isWeekend(date) && !isHoliday(date, normalizeMarket(market));
    }

    @Override
    public LocalDate previousTradingDay(LocalDate date, String market) {
        LocalDate cursor = date.minusDays(1);
        String normalizedMarket = normalizeMarket(market);

        while (!isTradingDay(cursor, normalizedMarket)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    @Override
    public LocalDate latestTradingDay(ZonedDateTime asOf, String market) {
        ZonedDateTime asOfKst = (asOf == null ? ZonedDateTime.now(KST) : asOf).withZoneSameInstant(KST);
        LocalDate today = asOfKst.toLocalDate();
        String normalizedMarket = normalizeMarket(market);

        if (!isTradingDay(today, normalizedMarket)) {
            return previousTradingDay(today, normalizedMarket);
        }

        if (asOfKst.toLocalTime().getHour() < MARKET_OPEN_HOUR) {
            return previousTradingDay(today, normalizedMarket);
        }

        return today;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    private boolean isHoliday(LocalDate date, String market) {
        try {
            return holidaySet(market, date.getYear()).contains(date);
        } catch (Exception ex) {
            log.warn("[TradingCalendar] holiday lookup failed. fallback=weekend-only, market={}, date={}",
                    market, date, ex);
            return false;
        }
    }

    private Set<LocalDate> holidaySet(String market, int year) {
        String cacheKey = holidayCacheKey(market, year);
        synchronized (yearlyHolidayCache) {
            Set<LocalDate> cached = yearlyHolidayCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            List<MarketHoliday> holidays = marketHolidayRepository.findByMarketAndHolidayDateBetween(market, start, end);
            Set<LocalDate> holidaySet = holidays.stream()
                    .map(MarketHoliday::getHolidayDate)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            yearlyHolidayCache.put(cacheKey, holidaySet);
            return holidaySet;
        }
    }

    private String holidayCacheKey(String market, int year) {
        return "market:holiday:" + market + ":" + year;
    }

    private String normalizeMarket(String market) {
        return (market == null || market.isBlank()) ? "KRX" : market.trim().toUpperCase();
    }
}
