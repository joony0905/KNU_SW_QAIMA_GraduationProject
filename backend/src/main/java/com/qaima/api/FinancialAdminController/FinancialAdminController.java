package com.qaima.api.FinancialAdminController;

import com.qaima.dto.FinancialDto;
import com.qaima.service.FinancialAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class FinancialAdminController {

    private final FinancialAdminService financialCommandService;

    @PostMapping("/stocks/{stockCode}/financials")
    public Mono<FinancialDto> createFinancial(
            @PathVariable String stockCode,
            @RequestParam(name = "exchange", required = false) String exchange,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.create(stockCode, exchange, dto);
    }

    @PutMapping("/financials/{id}")
    public Mono<FinancialDto> updateFinancial(
            @PathVariable Long id,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.update(id, dto);
    }

    @DeleteMapping("/financials/{id}")
    public Mono<Void> deleteFinancial(@PathVariable Long id) {
        return financialCommandService.delete(id);
    }
}
