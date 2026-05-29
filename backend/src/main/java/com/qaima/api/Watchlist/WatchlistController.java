package com.qaima.api.Watchlist;

import com.qaima.common.ApiResponse;
import com.qaima.dto.watchlist.MyWatchlistItemRequestDto;
import com.qaima.dto.watchlist.WatchlistItemUpdateDto;
import com.qaima.dto.watchlist.WatchlistRequestDto;
import com.qaima.dto.watchlist.WatchlistResponseDto;
import com.qaima.service.watchlist.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist")
@SecurityRequirement(name = "bearerAuth")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping("/me")
    @Operation(summary = "List my watchlist", description = "Returns the authenticated user's default watchlist items.")
    public Mono<ApiResponse<List<WatchlistResponseDto>>> getMyWatchlistItems(Authentication authentication) {
        return watchlistService.getDefaultWatchlistItems(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PostMapping("/me/items")
    @Operation(summary = "Add stock to my watchlist", description = "Adds a stock to the authenticated user's default watchlist.")
    public Mono<ApiResponse<WatchlistResponseDto>> addStockToMyWatchlist(
            Authentication authentication,
            @Valid @RequestBody MyWatchlistItemRequestDto requestDto
    ) {
        return watchlistService.addStockToDefaultWatchlist(requestDto, currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @GetMapping("/{watchlistId}")
    @Operation(summary = "List watchlist items")
    public Mono<ApiResponse<List<WatchlistResponseDto>>> getWatchlistItems(
            Authentication authentication,
            @PathVariable Long watchlistId
    ) {
        return watchlistService.getWatchlistItems(watchlistId, currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PostMapping("/items")
    @Operation(summary = "Add watchlist item")
    public Mono<ApiResponse<WatchlistResponseDto>> addStockToWatchlist(
            Authentication authentication,
            @Valid @RequestBody WatchlistRequestDto requestDto
    ) {
        return watchlistService.addStockToWatchlist(requestDto, currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Delete watchlist item", description = "Deletes a watchlist item by watchlist item id.")
    public Mono<ApiResponse<Void>> removeStockFromWatchlist(
            Authentication authentication,
            @PathVariable Long itemId
    ) {
        return watchlistService.removeStockFromWatchlist(itemId, currentUserId(authentication))
                .thenReturn(ApiResponse.success(null));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update watchlist item")
    public Mono<ApiResponse<WatchlistResponseDto>> updateWatchlistItemNote(
            Authentication authentication,
            @PathVariable Long itemId,
            @RequestBody WatchlistItemUpdateDto requestDto
    ) {
        return watchlistService.updateWatchlistItemNote(itemId, requestDto, currentUserId(authentication))
                .map(ApiResponse::success);
    }

    private static Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return userId;
    }
}
