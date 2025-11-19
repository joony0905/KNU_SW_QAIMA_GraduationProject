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
@Table(name = "industry")
    
public class Industry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long industryId;

    // (ERD: sector_id bigint [ref: > sector.sector_id])
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    @Column(length = 100, nullable = false, unique = true)
    private String name;

    @Column(length = 50)
    private String code;

    // 추후 이 산업군에 속한 주식 보기 기능 작업
    // @OneToMany(mappedBy = "industry")
    // private List<Stock> stocks = new ArrayList<>();
}
