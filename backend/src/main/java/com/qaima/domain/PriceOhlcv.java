package com.qaima.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "price_ohlcv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceOhlcv {

    @EmbeddedId
    private PriceOhlcvId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("stockId")
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "open", precision = 18, scale = 6)
    private BigDecimal open;

    @Column(name = "high", precision = 18, scale = 6)
    private BigDecimal high;

    @Column(name = "low", precision = 18, scale = 6)
    private BigDecimal low;

    @Column(name = "close", precision = 18, scale = 6)
    private BigDecimal close;

    @Column(name = "volume", precision = 20, scale = 0)
    private BigDecimal volume;
}
