package com.qaima.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "industry_index")
public class IndustryIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long indexId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 50)
    private String provider;

    @Column(length = 3)
    @ColumnDefault("'KRW'")
    private String currency;
}
