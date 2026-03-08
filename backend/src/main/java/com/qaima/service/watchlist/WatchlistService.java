package com.qaima.service.watchlist;

import com.qaima.common.Blocking;
import com.qaima.domain.Stock;
import com.qaima.domain.User;
import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import com.qaima.dto.watchlist.WatchlistItemUpdateDto;
import com.qaima.dto.watchlist.WatchlistRequestDto;
import com.qaima.dto.watchlist.WatchlistResponseDto;
import com.qaima.repository.StockRepository;
import com.qaima.repository.UserRepository;
import com.qaima.repository.WatchlistItemRepository;
import com.qaima.repository.WatchlistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    public Mono<WatchlistResponseDto> addStockToWatchlist(WatchlistRequestDto requestDto, Long userId) {
        Mono<User> userMono = loadUser(userId);
        Mono<Stock> stockMono = loadStock(requestDto.getStockId());
        Mono<Watchlist> watchlistMono = loadWatchlistWithUser(requestDto.getWatchlistId());

        return Mono.zip(userMono, stockMono, watchlistMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Stock stock = tuple.getT2();
                    Watchlist watchlist = tuple.getT3();

                    validateWatchlistOwnership(watchlist, user);

                    return Blocking.call(() ->
                                    watchlistItemRepository.existsByWatchlistAndStock_StockId(
                                            watchlist,
                                            stock.getStockId()
                                    ))
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new IllegalArgumentException("이미 관심목록에 등록된 종목입니다."));
                                }

                                WatchlistItem newItem = new WatchlistItem(watchlist, stock);
                                return Blocking.call(() -> {
                                            WatchlistItem saved = watchlistItemRepository.save(newItem);
                                            return watchlistItemRepository.findByIdWithRelations(saved.getWatchlistItemId())
                                                    .orElse(saved);
                                        })
                                        .map(WatchlistResponseDto::new);
                            });
                });
    }

    public Mono<List<WatchlistResponseDto>> getWatchlistItems(Long watchlistId, Long userId) {
        Mono<User> userMono = loadUser(userId);
        Mono<Watchlist> watchlistMono = loadWatchlistWithUser(watchlistId);

        return Mono.zip(userMono, watchlistMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Watchlist watchlist = tuple.getT2();

                    validateWatchlistOwnership(watchlist, user);
                    return Blocking.call(() -> watchlistItemRepository.findByWatchlistWithStock(watchlist));
                })
                .map(items -> items.stream().map(WatchlistResponseDto::new).toList());
    }

    public Mono<Void> removeStockFromWatchlist(Long watchlistItemId, Long userId) {
        Mono<User> userMono = loadUser(userId);
        Mono<WatchlistItem> itemMono = Blocking.call(() -> watchlistItemRepository.findByIdWithRelations(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")));

        return Mono.zip(userMono, itemMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    WatchlistItem item = tuple.getT2();

                    validateWatchlistOwnership(item.getWatchlist(), user);
                    return Blocking.run(() -> watchlistItemRepository.delete(item));
                });
    }

    public Mono<WatchlistResponseDto> updateWatchlistItemNote(
            Long watchlistItemId,
            WatchlistItemUpdateDto requestDto,
            Long userId
    ) {
        Mono<User> userMono = loadUser(userId);
        Mono<WatchlistItem> itemMono = Blocking.call(() -> watchlistItemRepository.findByIdWithRelations(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")));

        return Mono.zip(userMono, itemMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    WatchlistItem item = tuple.getT2();

                    validateWatchlistOwnership(item.getWatchlist(), user);

                    item.setNote(requestDto.getNote());
                    return Blocking.call(() -> {
                                WatchlistItem saved = watchlistItemRepository.save(item);
                                return watchlistItemRepository.findByIdWithRelations(saved.getWatchlistItemId())
                                        .orElse(saved);
                            })
                            .map(WatchlistResponseDto::new);
                });
    }

    private Mono<User> loadUser(Long userId) {
        return Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")));
    }

    private Mono<Stock> loadStock(Long stockId) {
        return Blocking.call(() -> stockRepository.findById(stockId)
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다.")));
    }

    private Mono<Watchlist> loadWatchlistWithUser(Long watchlistId) {
        return Blocking.call(() -> watchlistRepository.findByIdWithUser(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다.")));
    }

    private void validateWatchlistOwnership(Watchlist watchlist, User user) {
        if (watchlist == null
                || watchlist.getUser() == null
                || watchlist.getUser().getUserId() == null
                || user == null
                || user.getUserId() == null
                || !watchlist.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("관심목록 접근 권한이 없습니다.");
        }
    }
}
