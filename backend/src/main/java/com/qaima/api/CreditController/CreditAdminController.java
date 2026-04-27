package com.qaima.api.CreditController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.credit.CreditAdjustRequestDto;
import com.qaima.dto.credit.CreditLedgerDto;
import com.qaima.service.credit.CreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/credits")
@RequiredArgsConstructor
public class CreditAdminController {

    private final CreditService creditService;

    @PostMapping("/charge")
    public Mono<ApiResponse<CreditLedgerDto>> charge(@Valid @RequestBody CreditAdjustRequestDto request) {
        return creditService.charge(request.userId(), request.amount(), request.reason())
                .map(ApiResponse::success);
    }

    @PostMapping("/adjust")
    public Mono<ApiResponse<CreditLedgerDto>> adjust(@Valid @RequestBody CreditAdjustRequestDto request) {
        return creditService.adjust(request.userId(), request.amount(), request.reason())
                .map(ApiResponse::success);
    }
}
