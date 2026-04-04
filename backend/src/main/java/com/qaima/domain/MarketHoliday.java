package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "market_holiday",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_market_holiday", columnNames = {"market", "holiday_date"})
        },
        indexes = {
                @Index(name = "idx_market_holiday_market_date", columnList = "market, holiday_date")
        }
)
public class MarketHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market", nullable = false, length = 20)
    private String market;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
