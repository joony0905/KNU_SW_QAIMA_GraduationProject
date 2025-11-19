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
    public WatchlistResponseDto addStockToWatchlist(WatchlistRequestDto requestDto, Long userId) {
        // (나중에는 Spring Security에서 인증된 유저 정보를 가져와야 함)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        Stock stock = stockRepository.findById(requestDto.getStockId())
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다."));

        Watchlist watchlist = watchlistRepository.findById(requestDto.getWatchlistId())
                .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다."));

        // (추가: 이 유저가 이 관심목록의 주인인지 확인하는 로직 필요)

        WatchlistItem newItem = new WatchlistItem(watchlist, stock);
        WatchlistItem savedItem = watchlistItemRepository.save(newItem);

        return new WatchlistResponseDto(savedItem);
    }

    /*
     *  조회
     */
    @Transactional(readOnly = true)
    public List<WatchlistResponseDto> getWatchlistItems(Long watchlistId, Long userId) {
        // (나중에 Security에서 인증된 유저 정보를 가져와야 함)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new IllegalArgumentException("관심목록을 찾을 수 없습니다."));

        // (추가: 이 유저가 이 관심목록의 주인인지 확인하는 로직 필요)

        List<WatchlistItem> items = watchlistItemRepository.findByWatchlist(watchlist);

        // Entity List -> DTO List로 변환
        return items.stream()
                .map(WatchlistResponseDto::new)
                .collect(Collectors.toList());
    }

    /*
     * 삭제
     */
    @Transactional
    public void removeStockFromWatchlist(Long watchlistItemId, Long userId) {
        // (나중에 Security에서 인증된 유저 정보를 가져와야 함)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        WatchlistItem item = watchlistItemRepository.findById(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다."));

        // (추가: 이 유저가 이 아이템의 주인인지 확인하는 로직 필요)

        watchlistItemRepository.delete(item);

    }

    /*
     *  갱신
     */
    @Transactional
    public WatchlistResponseDto updateWatchlistItemNote(Long watchlistItemId, WatchlistItemUpdateDto requestDto, Long userId) {
        // (임시) 유저 ID로 유저 찾기
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        // 수정할 아이템 찾기
        WatchlistItem item = watchlistItemRepository.findById(watchlistItemId)
                .orElseThrow(() -> new IllegalArgumentException("아이템을 찾을 수 없습니다."));

        // (중요!) 이 아이템이 이 유저의 소유가 맞는지 확인
        // (item -> watchlist -> user의 ID가 1L인지 확인)
        if (!item.getWatchlist().getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }

        item.setNote(requestDto.getNote());

        // (변경 감지(Dirty Checking)로 자동 저장되지만, 명시적으로 save 호출)
        WatchlistItem updatedItem = watchlistItemRepository.save(item);

        return new WatchlistResponseDto(updatedItem);
    }

}
