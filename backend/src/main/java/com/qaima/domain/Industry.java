package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;
import java.util.ArrayList;

@Getter
@NoArgsConstructor
@Setter
@Entity
@Table(
        name = "industry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_industry_exchange_sector_scheme_code", columnNames = {"exchange_id", "sector_id", "scheme", "code"})
        }
)
public class Industry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long industryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @Column(length = 30)
    private String scheme; // e.g., KRX_BZTP_S

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 50)
    private String code;

    // 추후 이 산업군에 속한 주식 보기 기능 작업 + 26/2/26 기준 scheme도 참조
    // @OneToMany(mappedBy = "industry")
    // private List<Stock> stocks = new ArrayList<>();
}
