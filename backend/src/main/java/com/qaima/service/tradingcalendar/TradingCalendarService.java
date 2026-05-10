package com.qaima.service.tradingcalendar;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public interface TradingCalendarService {

    boolean isTradingDay(LocalDate date, String market);

    LocalDate previousTradingDay(LocalDate date, String market);

    LocalDate nextTradingDay(LocalDate date, String market);

    LocalDate latestTradingDay(ZonedDateTime asOf, String market);
}
