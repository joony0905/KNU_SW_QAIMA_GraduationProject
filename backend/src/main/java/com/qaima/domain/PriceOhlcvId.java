package com.qaima.domain;

import com.qaima.domain.converter.FreqConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PriceOhlcvId implements Serializable {

    private Long stockId;
    private OffsetDateTime ts;

    @Convert(converter = FreqConverter.class)
    @Column(name = "freq")
    private Freq freq;
}
