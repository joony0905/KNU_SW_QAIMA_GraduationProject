package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "industry_index_ohlcv")
public class IndustryIndexOhlcv {

    @EmbeddedId
    private IndustryIndexOhlcvId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("indexId")
    @JoinColumn(name = "index_id", nullable = false)
    private IndustryIndex industryIndex;

    @Column(precision = 18, scale = 6)
    private BigDecimal open;

    @Column(precision = 18, scale = 6)
    private BigDecimal high;

    @Column(precision = 18, scale = 6)
    private BigDecimal low;

    @Column(precision = 18, scale = 6)
    private BigDecimal close;

    @Column(precision = 20, scale = 0)
    private BigDecimal volume;
}
