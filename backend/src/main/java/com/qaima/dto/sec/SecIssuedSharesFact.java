package com.qaima.dto.sec;

import java.time.LocalDate;

public record SecIssuedSharesFact(
        String cik,
        String entityName,
        String accessionNumber,
        LocalDate endDate,
        LocalDate filedDate,
        String form,
        Integer fiscalYear,
        String fiscalPeriod,
        Long sharesOutstanding
) {
}
