package com.qaima.service;

import com.qaima.domain.Stock;
import com.qaima.domain.User;
import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import com.qaima.dto.WatchlistItemUpdateDto;
import com.qaima.dto.WatchlistRequestDto;
import com.qaima.dto.WatchlistResponseDto;
import com.qaima.repository.StockRepository;
import com.qaima.repository.UserRepository;
import com.qaima.repository.WatchlistItemRepository;
import com.qaima.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;

    // 관심종목 추가

    @Transactional
    public Mono<WatchlistResponseDto> addStockToWatchlist(WatchlistRequestDto requestDto, Long userId) {
        // (나중에는 Spring Security에서 인증된 유저 정보를 가져와야 함)
        Mono<User> userMono = Mono.fromCallable(() -> userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Stock> stockMono = Mono.fromCallable(() -> stockRepository.findById(requestDto.getStockId())
                        .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Watchlist> watchlistMono = Mono.fromCallable(() -> watchlistRepository.findById(requestDto.getWatchlistId())
                        .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(userMono, stockMono, watchlistMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT2();
                    Watchlist watchlist = tuple.getT3();

                    // (추가: 이 유저가 이 관심목록의 주인인지 확인하는 로직 필요)
                    WatchlistItem newItem = new WatchlistItem(watchlist, stock);

                    return Mono.fromCallable(() -> watchlistItemRepository.save(newItem))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(WatchlistResponseDto::new);
                });
    }

    /*
     *  조회
     */
    @Transactional(readOnly = true)
    public Mono<List<WatchlistResponseDto>> getWatchlistItems(Long watchlistId, Long userId) {
        // (나중에 Security에서 인증된 유저 정보를 가져와야 함)
        Mono<User> userMono = Mono.fromCallable(() -> userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<Watchlist> watchlistMono = Mono.fromCallable(() -> watchlistRepository.findById(watchlistId)
                        .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        return userMono.then(watchlistMono)
                .flatMap(watchlist -> Mono.fromCallable(() -> watchlistItemRepository.findByWatchlist(watchlist))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(items -> items.stream()
                        .map(WatchlistResponseDto::new)
                        .collect(Collectors.toList()));
    }

    /*
     * 삭제
     */
    @Transactional
    public Mono<Void> removeStockFromWatchlist(Long watchlistItemId, Long userId) {
        // (나중에 Security에서 인증된 유저 정보를 가져와야 함)
        Mono<User> userMono = Mono.fromCallable(() -> userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<WatchlistItem> itemMono = Mono.fromCallable(() -> watchlistItemRepository.findById(watchlistItemId)
                        .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        return userMono.then(itemMono)
                .flatMap(item -> Mono.fromCallable(() -> {
                            watchlistItemRepository.delete(item);
                            return null;
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    /*
     *  갱신
     */
    @Transactional
    public Mono<WatchlistResponseDto> updateWatchlistItemNote(Long watchlistItemId, WatchlistItemUpdateDto requestDto, Long userId) {
        // (임시) 유저 ID로 유저 찾기
        Mono<User> userMono = Mono.fromCallable(() -> userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<WatchlistItem> itemMono = Mono.fromCallable(() -> watchlistItemRepository.findById(watchlistItemId)
                        .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다.")))
                .subscribeOn(Schedulers.boundedElastic());

        return userMono.then(itemMono)
                .flatMap(item -> {
                    // (중요!) 이 아이템이 이 유저의 소유가 맞는지 확인
                    if (!item.getWatchlist().getUser().getUserId().equals(userId)) {
                        return Mono.error(new IllegalArgumentException("수정 권한이 없습니다."));
                    }

                    item.setNote(requestDto.getNote());

                    return Mono.fromCallable(() -> watchlistItemRepository.save(item))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(WatchlistResponseDto::new);
                });
    }

}
