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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    public Mono<WatchlistResponseDto> addStockToWatchlist(WatchlistRequestDto requestDto, Long userId) {
        Mono<User> userMono = Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")));

        Mono<Stock> stockMono = Blocking.call(() -> stockRepository.findById(requestDto.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다.")));

        Mono<Watchlist> watchlistMono = Blocking.call(() -> watchlistRepository.findById(requestDto.getWatchlistId())
                .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다.")));

        return Mono.zip(userMono, stockMono, watchlistMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Stock stock = tuple.getT2();
                    Watchlist watchlist = tuple.getT3();
                    if (watchlist.getUser() == null || watchlist.getUser().getUserId() == null ||
                            !watchlist.getUser().getUserId().equals(user.getUserId())) {
                        return Mono.error(new IllegalArgumentException("관심목록 접근 권한이 없습니다."));
                    }

                    WatchlistItem newItem = new WatchlistItem(watchlist, stock);

                    return Blocking.call(() -> watchlistItemRepository.save(newItem))
                            .map(WatchlistResponseDto::new);
                });
    }

    public Mono<List<WatchlistResponseDto>> getWatchlistItems(Long watchlistId, Long userId) {
        Mono<User> userMono = Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")));

        Mono<Watchlist> watchlistMono = Blocking.call(() -> watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다.")));

        return Mono.zip(userMono, watchlistMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    Watchlist watchlist = tuple.getT2();

                    if (watchlist.getUser() == null || watchlist.getUser().getUserId() == null ||
                            !watchlist.getUser().getUserId().equals(user.getUserId())) {
                        return Mono.error(new IllegalArgumentException("관심목록 접근 권한이 없습니다."));
                    }

                    return Blocking.call(() -> watchlistItemRepository.findByWatchlist(watchlist));
                })
                .map(items -> items.stream().map(WatchlistResponseDto::new).toList());
    }

    public Mono<Void> removeStockFromWatchlist(Long watchlistItemId, Long userId) {
        Mono<User> userMono = Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")));

        Mono<WatchlistItem> itemMono = Blocking.call(() -> watchlistItemRepository.findById(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")));

        return Mono.zip(userMono, itemMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    WatchlistItem item = tuple.getT2();

                    if (item.getWatchlist() == null || item.getWatchlist().getUser() == null ||
                            item.getWatchlist().getUser().getUserId() == null ||
                            !item.getWatchlist().getUser().getUserId().equals(user.getUserId())) {
                        return Mono.error(new IllegalArgumentException("삭제 권한이 없습니다."));
                    }

                    return Blocking.run(() -> watchlistItemRepository.delete(item));
                });
    }

    public Mono<WatchlistResponseDto> updateWatchlistItemNote(Long watchlistItemId,
                                                              WatchlistItemUpdateDto requestDto,
                                                              Long userId) {
        Mono<User> userMono = Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")));

        Mono<WatchlistItem> itemMono = Blocking.call(() -> watchlistItemRepository.findById(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")));

        return Mono.zip(userMono, itemMono)
                .flatMap(tuple -> {
                    User user = tuple.getT1();
                    WatchlistItem item = tuple.getT2();

                    if (item.getWatchlist() == null || item.getWatchlist().getUser() == null ||
                            item.getWatchlist().getUser().getUserId() == null ||
                            !item.getWatchlist().getUser().getUserId().equals(user.getUserId())) {
                        return Mono.error(new IllegalArgumentException("수정 권한이 없습니다."));
                    }

                    item.setNote(requestDto.getNote());

                    return Blocking.call(() -> watchlistItemRepository.save(item))
                            .map(WatchlistResponseDto::new);
                });
    }
}
