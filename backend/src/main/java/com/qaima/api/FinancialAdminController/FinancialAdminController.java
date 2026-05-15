package com.qaima.api.FinancialAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.financial.FinancialDto;
import com.qaima.importer.FinancialImportService;
import com.qaima.service.financial.FinancialAdminService;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class FinancialAdminController {

    private final FinancialAdminService financialCommandService;
    private final FinancialImportService financialImportService;
    
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

    @PostMapping("/financials/import-csv")
    public Mono<ApiResponse<FinancialImportService.ImportResult>> importFinancialCsv(
            @RequestParam String path,
            @RequestParam(required = false) String exchange
    ) {
        return Mono.fromCallable(() -> financialImportService.importFromCsv(Path.of(path), exchange))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ApiResponse::success);
    }
}
