package com.qaima.api.CreditController;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.credit.CreditBalanceDto;
import com.qaima.dto.credit.CreditLedgerDto;
import com.qaima.dto.credit.CreditTempChargeRequestDto;
import com.qaima.service.credit.CreditService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/balance")
    public Mono<ApiResponse<CreditBalanceDto>> balance(Authentication authentication) {
        return creditService.getBalance(currentUserId(authentication))
                .map(ApiResponse::success);
    }

    @GetMapping("/ledger")
    public Mono<ApiResponse<List<CreditLedgerDto>>> ledger(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return creditService.getLedger(currentUserId(authentication), limit)
                .map(ApiResponse::success);
    }

    @PostMapping("/temp-charge")
    public Mono<ApiResponse<CreditLedgerDto>> tempCharge(
            Authentication authentication,
            @Valid @RequestBody CreditTempChargeRequestDto request
    ) {
        return creditService.charge(
                        currentUserId(authentication),
                        request.amount(),
                        request.reason() == null || request.reason().isBlank()
                                ? "TEMP_FRONTEND_CHARGE"
                                : request.reason()
                )
                .map(ApiResponse::success);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }
}
