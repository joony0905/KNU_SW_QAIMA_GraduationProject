package com.qaima.domain;

import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "watchlist_item", uniqueConstraints = {
        // indexes { (watchlist_id, stock_id) [unique] }
        @UniqueConstraint(name = "uk_watchlist_stock", columnNames = {"watchlist_id", "stock_id"})
})
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // watchlist_item_id bigserial [pk]
    private Long watchlistItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", nullable = false) // watchlist_id bigint [not null, ref: > watchlist.watchlist_id]
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false) // stock_id bigint [not null, ref: > stock.stock_id]
    private Stock stock;

    private Integer position; // position int (사용자 지정 순서)

    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt; // created_at timestamptz [not null, default: 'now()']

    public WatchlistItem(Watchlist watchlist, Stock stock) {
        this.watchlist = watchlist;
        this.stock = stock;
    }
}

