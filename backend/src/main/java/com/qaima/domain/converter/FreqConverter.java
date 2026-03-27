package com.qaima.domain.converter;

import com.qaima.domain.Freq;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FreqConverter implements AttributeConverter<Freq, Byte> {

    @Override
    public Byte convertToDatabaseColumn(Freq attribute) {
        if (attribute == null) return null;

        return switch (attribute) {
            case ONE_MIN -> (byte)0;
            case FIVE_MIN -> (byte)1;
            case FIFTEEN_MIN -> (byte)2;
            case ONE_H -> (byte)3;
            case ONE_D -> (byte)4;
            case ONE_W -> (byte)5;
            case ONE_M -> (byte)6;
        };
    }

    @Override
    public Freq convertToEntityAttribute(Byte dbData) {
        if (dbData == null) return null;

        return switch (dbData) {
            case 0 -> Freq.ONE_MIN;
            case 1 -> Freq.FIVE_MIN;
            case 2 -> Freq.FIFTEEN_MIN;
            case 3 -> Freq.ONE_H;
            case 4 -> Freq.ONE_D;
            case 5 -> Freq.ONE_W;
            case 6 -> Freq.ONE_M;
            default -> throw new IllegalArgumentException("Unknown freq code: " + dbData);
        };
    }
}