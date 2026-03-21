package com.qaima.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter(autoApply = true)
public class PeriodTypeConverter implements AttributeConverter<PeriodType, String> {

    @Override
    public String convertToDatabaseColumn(PeriodType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public PeriodType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return PeriodType.valueOf(dbData.trim().toUpperCase(Locale.ROOT));
    }
}
