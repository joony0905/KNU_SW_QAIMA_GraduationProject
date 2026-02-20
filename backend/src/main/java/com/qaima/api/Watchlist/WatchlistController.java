package com.qaima.api.Watchlist;

import com.qaima.common.ApiResponse;
import com.qaima.dto.WatchlistDto;
import com.qaima.dto.WatchlistRequestDto;
import com.qaima.dto.WatchlistResponseDto;
import com.qaima.dto.WatchlistItemUpdateDto;
import com.qaima.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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

    @GetMapping
    public Mono<ApiResponse<List<WatchlistDto>>> getMyWatchlists(Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();
        return watchlistService.getMyWatchlists(currentUserId)
                .map(ApiResponse::success);
    }

    @GetMapping("/{watchlistId}")
    public Mono<ApiResponse<List<WatchlistResponseDto>>> getWatchlistItems(@PathVariable Long watchlistId,
                                                                           Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();
        return watchlistService.getWatchlistItems(watchlistId, currentUserId)
                .map(ApiResponse::success);
    }

    /*
     * 생성
     * POST /api/v1/watchlist/items
     */
    @PostMapping("/items")
    public Mono<ApiResponse<WatchlistResponseDto>> addStockToWatchlist(@RequestBody WatchlistRequestDto requestDto,
                                                                       Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();
        return watchlistService.addStockToWatchlist(requestDto, currentUserId)
                .map(ApiResponse::success);
    }

    /*
     * 삭제
     * DELETE /api/v1/watchlist/items/{itemId}
     */
    @DeleteMapping("/items/{itemId}")
    public Mono<ApiResponse<Void>> removeStockFromWatchlist(@PathVariable Long itemId,
                                                            Authentication authentication) {
        Long currentUserId = (Long) authentication.getPrincipal();
        return watchlistService.removeStockFromWatchlist(itemId, currentUserId)
                .thenReturn(ApiResponse.success(null));
    }

    /*
     * 갱신
     * DELETE /api/v1/watchlist/items/{itemId}
     */

    @PatchMapping("/items/{itemId}")
    public Mono<ApiResponse<WatchlistResponseDto>> updateWatchlistItemNote(
            @PathVariable Long itemId,
            @RequestBody WatchlistItemUpdateDto requestDto,
            Authentication authentication
    ) {
        Long currentUserId = (Long) authentication.getPrincipal();
        return watchlistService.updateWatchlistItemNote(itemId, requestDto, currentUserId)
                .map(ApiResponse::success);
    }
}
