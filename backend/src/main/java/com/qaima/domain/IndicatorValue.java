package com.qaima.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

//일단 초기단계에서는 Python내에서 계산하고 바로바로 뿌림.
//나중에 기능1관련 구현이 다되면 그때 종가기준 하나씩 넣는 느낌으로
@Entity
@Table(
        name = "indicator_value",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_indicator_unique",
                columnNames = {"stock_id", "ts", "freq", "indicator_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;   // surrogate PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "ts", nullable = false)
    private OffsetDateTime ts;

    @Enumerated(EnumType.STRING)
    @Column(name = "freq", nullable = false)
    private Freq freq;

    @Column(name = "indicator_key", nullable = false, length = 50)
    private String key; // "STOCHRSI_14_3_3", "BB_20_2" 등

    @Column(name = "value_num", precision = 20, scale = 8)
    private BigDecimal valueNum;

    @Column(name = "value_json")//, columnDefinition = "jsonb")
    private String valueJson; // JSONB 문자열
}
