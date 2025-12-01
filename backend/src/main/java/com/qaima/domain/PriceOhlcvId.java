package com.qaima.domain;

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

    private Freq freq; // Enum
}
