package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.User;
import com.qaima.domain.Stock;
import com.qaima.domain.Watchlist;
import com.qaima.domain.WatchlistItem;
import com.qaima.dto.WatchlistDto;
import com.qaima.dto.WatchlistItemUpdateDto;
import com.qaima.dto.WatchlistRequestDto;
import com.qaima.dto.WatchlistResponseDto;
import com.qaima.repository.StockRepository;
import com.qaima.repository.UserRepository;
import com.qaima.repository.WatchlistItemRepository;
import com.qaima.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private static final String DEFAULT_WATCHLIST_NAME = "관심목록";
    private static final String DEFAULT_EXCHANGE_CODE = "KOSPI";

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;

    public Mono<WatchlistResponseDto> addStockToWatchlist(WatchlistRequestDto requestDto, Long userId) {
        Long watchlistId = requestDto.getWatchlistId();

        String stockCode = requestDto.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        String exchangeCode = normalizeExchangeCode(requestDto.getExchangeCode());
        String resolvedExchange = exchangeCode != null ? exchangeCode : DEFAULT_EXCHANGE_CODE;

        Mono<Watchlist> watchlistMono = resolveTargetWatchlist(watchlistId, userId);

        Mono<Stock> stockMono = Blocking.call(() -> stockRepository
                .findByExchangeCodeAndStockCodeIgnoreCase(resolvedExchange, stockCode)
                .orElseThrow(() -> new IllegalArgumentException("Stock not found")));

        return Mono.zip(watchlistMono, stockMono)
                .flatMap(tuple -> {
                    Watchlist watchlist = tuple.getT1();
                    Stock stock = tuple.getT2();
                    WatchlistItem newItem = new WatchlistItem(watchlist, stock);
                    return Blocking.<WatchlistItem>call(() -> watchlistItemRepository.save(newItem))
                            .map(WatchlistResponseDto::new);
                });
    }

    public Mono<List<WatchlistResponseDto>> getWatchlistItems(Long watchlistId, Long userId) {
        return Blocking.call(() -> watchlistItemRepository.findOwnedItemsByWatchlistIdWithStock(watchlistId, userId))
                .map(items -> items.stream().map(WatchlistResponseDto::new).toList());
    }

    public Mono<List<WatchlistDto>> getMyWatchlists(Long userId) {
        return Blocking.call(() -> watchlistRepository.findByUser_UserIdOrderByWatchlistIdAsc(userId))
                .map(list -> (list == null ? List.<Watchlist>of() : list).stream()
                        .map(WatchlistDto::new)
                        .toList());
    }

    public Mono<Void> removeStockFromWatchlist(Long watchlistItemId, Long userId) {
        return Blocking.call(() -> watchlistItemRepository.findOwnedByIdWithAll(watchlistItemId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("?꾩씠?쒖쓣 李얠쓣 ???놁뒿?덈떎.")))
                .flatMap(item -> Blocking.run(() -> watchlistItemRepository.delete(item)));
    }

    public Mono<WatchlistResponseDto> updateWatchlistItemNote(Long watchlistItemId,
                                                              WatchlistItemUpdateDto requestDto,
                                                              Long userId) {
        return Blocking.call(() -> watchlistItemRepository.findOwnedByIdWithAll(watchlistItemId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("?꾩씠?쒖쓣 李얠쓣 ???놁뒿?덈떎.")))
                .flatMap(item -> {
                    item.setNote(requestDto.getNote());
                    return Blocking.<WatchlistItem>call(() -> watchlistItemRepository.save(item))
                            .map(WatchlistResponseDto::new);
                });
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) {
            return null;
        }
        String trimmed = exchangeCode.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return switch (trimmed.toUpperCase()) {
            case "XKRX", "KRX" -> "KOSPI";
            case "XKOS" -> "KOSDAQ";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> trimmed.toUpperCase();
        };
    }

    private Mono<Watchlist> resolveTargetWatchlist(Long watchlistId, Long userId) {
        if (watchlistId != null) {
            return Blocking.call(() -> watchlistRepository
                    .findOwnedByIdWithUser(watchlistId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("愿?щぉ濡앹쓣 李얠쓣 ???놁뒿?덈떎.")));
        }
        return getOrCreateDefaultWatchlist(userId);
    }

    private Mono<Watchlist> getOrCreateDefaultWatchlist(Long userId) {
        return Blocking.call(() -> watchlistRepository.findByUser_UserIdOrderByWatchlistIdAsc(userId))
                .flatMap(existing -> {
                    if (existing != null && !existing.isEmpty()) {
                        return Mono.just(existing.get(0));
                    }

                    User userRef = userRepository.getReferenceById(userId);
                    Watchlist w = new Watchlist();
                    w.setUser(userRef);
                    w.setName(DEFAULT_WATCHLIST_NAME);

                    return Blocking.call(() -> watchlistRepository.save(w))
                            .onErrorResume(DataIntegrityViolationException.class, e ->
                                    Blocking.call(() -> watchlistRepository.findByUser_UserIdOrderByWatchlistIdAsc(userId))
                                            .flatMap(reloaded -> {
                                                if (reloaded == null || reloaded.isEmpty()) {
                                                    return Mono.error(e);
                                                }
                                                return Mono.just(reloaded.get(0));
                                            })
                            );
                });
    }
}
