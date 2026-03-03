package com.qaima.domain;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
@Entity
@Table(name = "watchlist", uniqueConstraints = {
        // indexes { (user_id, name) [unique] }
        @UniqueConstraint(name = "uk_user_watchlist_name", columnNames = {"user_id", "name"})
})
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // watchlist_id bigserial [pk]
    private Long watchlistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false) // user_id bigint [not null, ref: > users.user_id]
    private User user;

    @Column(nullable = false, length = 50)
    @ColumnDefault("'관심'") // name varchar(50) [not null, default: '관심']
    private String name;

    @Column(length = 50)
    private String sortPref; // sort_pref varchar(50)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt; // created_at timestamptz [not null, default: 'now()']

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt; // updated_at timestamptz [not null, default: 'now()']
}
