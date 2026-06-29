package com.qaima.api.portfolio;

import com.qaima.common.ApiResponse;
import com.qaima.dto.portfolio.PortfolioResponseDto;
import com.qaima.dto.portfolio.PortfolioSaveRequestDto;
import com.qaima.service.portfolio.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
@Tag(name = "Portfolio")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/me/default")
    @Operation(summary = "Get my default portfolio", description = "Returns the authenticated user's saved default portfolio.")
    public Mono<ApiResponse<PortfolioResponseDto>> getMyPortfolio(Authentication authentication) {
        return portfolioService.getMyPortfolio(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @PutMapping("/me/default")
    @Operation(summary = "Save my default portfolio", description = "Replaces the authenticated user's default portfolio holdings.")
    public Mono<ApiResponse<PortfolioResponseDto>> replaceMyPortfolio(
            Authentication authentication,
            @Valid @RequestBody PortfolioSaveRequestDto request
    ) {
        return portfolioService.replaceMyPortfolio(currentUserId(authentication), request)
                .map(ApiResponse::success);
    }

    private static Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return userId;
    }
}
