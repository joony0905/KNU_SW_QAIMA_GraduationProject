package com.qaima.api.FinancialAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.financial.FinancialDto;
import com.qaima.service.financial.FinancialAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class FinancialAdminController {

    private final FinancialAdminService financialCommandService;
    
    @PostMapping("/stocks/{stockCode}/financials")
    public Mono<ApiResponse<FinancialDto>> createFinancial(
            @PathVariable String stockCode,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.create(stockCode, dto)
                .map(ApiResponse::success);
    }

    @PutMapping("/financials/{id}")
    public Mono<ApiResponse<FinancialDto>> updateFinancial(
            @PathVariable Long id,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.update(id, dto)
                .map(ApiResponse::success);
    }

    @DeleteMapping("/financials/{id}")
    public Mono<ApiResponse<Void>> deleteFinancial(@PathVariable Long id) {
        return financialCommandService.delete(id)
                .thenReturn(ApiResponse.success(null));
    }
}
