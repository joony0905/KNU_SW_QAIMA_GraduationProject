package com.qaima.controller;

import com.qaima.common.ApiResponse;
import com.qaima.dto.WatchlistRequestDto;
import com.qaima.dto.WatchlistResponseDto;
import com.qaima.dto.WatchlistItemUpdateDto;
import com.qaima.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;
    // private final UserService userService; // 나중에 로그인한 유저 정보 가져올 때 활성화

    /*
     * 조회
     * GET /api/v1/watchlist/{watchlistId}
     */
    @GetMapping("/{watchlistId}")
    public ApiResponse<List<WatchlistResponseDto>> getWatchlistItems(@PathVariable Long watchlistId) {
        Long currentUserId = 1L;
        List<WatchlistResponseDto> items = watchlistService.getWatchlistItems(watchlistId, currentUserId);
        return ApiResponse.success(items);
    }

    /*
     * 생성
     * POST /api/v1/watchlist/items
     */
    @PostMapping("/items")
    public ApiResponse<WatchlistResponseDto> addStockToWatchlist(@RequestBody WatchlistRequestDto requestDto) {
        Long currentUserId = 1L;
        WatchlistResponseDto newItem = watchlistService.addStockToWatchlist(requestDto, currentUserId);
        return ApiResponse.success(newItem);
    }

    /*
     * 삭제
     * DELETE /api/v1/watchlist/items/{itemId}
     */
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<?> removeStockFromWatchlist(@PathVariable Long itemId) {
        Long currentUserId = 1L;
        watchlistService.removeStockFromWatchlist(itemId, currentUserId);
        return ApiResponse.success(null);
    }

    /*
     * 갱신
     * DELETE /api/v1/watchlist/items/{itemId}
     */
  
    @PatchMapping("/items/{itemId}")
    public ApiResponse<WatchlistResponseDto> updateWatchlistItemNote(
            @PathVariable Long itemId,
            @RequestBody WatchlistItemUpdateDto requestDto) {
        Long currentUserId = 1L;
        WatchlistResponseDto updatedItem = watchlistService.updateWatchlistItemNote(itemId, requestDto, currentUserId);
        return ApiResponse.success(updatedItem);
    }
}
